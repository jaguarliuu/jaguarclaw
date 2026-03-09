package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PolicySupervisor Tests")
class PolicySupervisorTest {

    private final PolicySupervisor supervisor = new PolicySupervisor();

    @Test
    @DisplayName("should default to heavy when no high confidence blocker signal exists")
    void shouldDefaultToHeavyWhenNoHighConfidenceBlockerSignalExists() {
        PolicyDecision decision = supervisor.evaluate("今天几号？", List.of());

        assertEquals(TaskComplexity.HEAVY, decision.complexity());
        assertTrue(decision.enterHeavyLoop());
        assertNull(decision.outcome());
    }

    @Test
    @DisplayName("should not block runtime observations directly")
    void shouldNotBlockRuntimeObservationsDirectly() {
        PolicyDecision decision = supervisor.evaluate(
                "监控推特账号并用 RSS.app 推送",
                List.of("RSS.app requires paid plan")
        );

        assertTrue(decision.enterHeavyLoop());
        assertNull(decision.outcome());
    }
}
