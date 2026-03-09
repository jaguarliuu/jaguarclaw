package com.jaguarliu.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运行时 fallback policy。
 * 只在无法进行语义路由时提供保守的执行复杂度默认值，
 * 不再基于文本内容直接做运行时 blocked 判断。
 */
@Component
public class PolicySupervisor {

    public PolicyDecision evaluate(String userPrompt, List<String> observations) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return PolicyDecision.heavy("");
        }
        return PolicyDecision.heavy(userPrompt.trim());
    }
}
