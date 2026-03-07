package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("PolicySupervisor Tests")
class PolicySupervisorTest {

    private final PolicySupervisor supervisor = new PolicySupervisor();

    @Test
    @DisplayName("should classify simple date question as direct response")
    void shouldClassifySimpleDateQuestionAsDirectResponse() {
        PolicyDecision decision = supervisor.evaluate("今天几号？", List.of());

        assertEquals(TaskComplexity.DIRECT, decision.complexity());
        assertFalse(decision.enterHeavyLoop());
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
}
