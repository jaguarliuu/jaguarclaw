package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 进入重型执行前的策略门。
 */
@Component
public class PolicySupervisor {

    public PolicyDecision evaluate(String userPrompt, List<String> observations) {
        String prompt = userPrompt == null ? "" : userPrompt.trim();
        String normalized = prompt.toLowerCase(Locale.ROOT);

        if (containsPaidSignal(observations)) {
            return PolicyDecision.blocked(
                    TaskComplexity.EXTERNAL_DEPENDENCY,
                    new RunOutcome(
                            RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                            "Task requires user decision",
                            firstObservation(observations)
                    ),
                    prompt
            );
        }

        if (isDirectQuestion(normalized)) {
            return PolicyDecision.direct(prompt);
        }

        if (normalized.contains("rss.app")
                || normalized.contains("twitter")
                || normalized.contains("x.com")
                || normalized.contains("推特")
                || normalized.contains("监控")
                || normalized.contains("login")
                || normalized.contains("授权")
                || normalized.contains("subscription")) {
            return PolicyDecision.external(prompt);
        }

        if (normalized.contains("pdf")
                || normalized.contains("整理")
                || normalized.contains("报告")
                || normalized.contains("导出")
                || normalized.contains("总结")
                || normalized.contains("生成")) {
            return PolicyDecision.heavy(prompt);
        }

        return PolicyDecision.light(prompt);
    }

    private boolean isDirectQuestion(String normalized) {
        return normalized.contains("今天几号")
                || normalized.contains("今天是几号")
                || normalized.contains("有多少内存")
                || normalized.contains("几点")
                || normalized.startsWith("what is")
                || normalized.startsWith("when is")
                || normalized.startsWith("how much memory")
                || normalized.startsWith("what day");
    }

    private boolean containsPaidSignal(List<String> observations) {
        if (observations == null) {
            return false;
        }
        return observations.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.contains("paid plan")
                        || item.contains("subscription")
                        || item.contains("requires payment")
                        || item.contains("付费"));
    }

    private String firstObservation(List<String> observations) {
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        return observations.stream().filter(item -> item != null && !item.isBlank()).findFirst().orElse(null);
    }
}
