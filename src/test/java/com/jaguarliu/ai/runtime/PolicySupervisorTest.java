package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PolicySupervisor Tests")
class PolicySupervisorTest {

    private final PolicySupervisor supervisor = new PolicySupervisor();

    @Test
    @DisplayName("should default to heavy when no high confidence blocker signal exists")
    void shouldDefaultToHeavyWhenNoHighConfidenceBlockerSignalExists() {
        PolicyDecision decision = supervisor.evaluate("今天几号？", List.of());

        assertEquals(TaskComplexity.HEAVY, decision.complexity());
    }

    @Test
    @DisplayName("should block paid service flow pending user decision")
    void shouldBlockPaidServiceFlowPendingUserDecision() {
        PolicyDecision decision = supervisor.evaluate(
                "监控推特账号并用 RSS.app 推送",
                List.of("RSS.app requires paid plan")
        );

        assertEquals(RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION,
                decision.outcome().status());
    }

    @Test
    @DisplayName("should block environment missing signal")
    void shouldBlockEnvironmentMissingSignal() {
        PolicyDecision decision = supervisor.evaluate(
                "导出 PDF",
                List.of("wkhtmltopdf: command not found")
        );

        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, decision.outcome().status());
    }
}
