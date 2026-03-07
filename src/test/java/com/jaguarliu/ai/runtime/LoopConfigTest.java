package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("LoopConfig Tests")
class LoopConfigTest {

    @Test
    @DisplayName("withMaxSteps should preserve budget settings")
    void withMaxStepsShouldPreserveBudgetSettings() {
        LoopConfig base = LoopConfig.builder()
                .maxSteps(5)
                .runTimeoutSeconds(60)
                .stepTimeoutSeconds(20)
                .maxTokens(2000)
                .maxCostUsd(1.5)
                .maxRepeatedFailures(3)
                .maxLowProgressRounds(4)
                .maxEnvironmentRepairAttempts(2)
                .build();

        LoopConfig config = LoopConfig.withMaxSteps(9, base);

        assertEquals(9, config.getMaxSteps());
        assertEquals(60, config.getRunTimeoutSeconds());
        assertEquals(20, config.getStepTimeoutSeconds());
        assertEquals(2000, config.getMaxTokens());
        assertEquals(1.5, config.getMaxCostUsd());
        assertEquals(3, config.getMaxRepeatedFailures());
        assertEquals(4, config.getMaxLowProgressRounds());
        assertEquals(2, config.getMaxEnvironmentRepairAttempts());
    }
}
