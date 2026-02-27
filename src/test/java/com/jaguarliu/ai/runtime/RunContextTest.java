package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RunContext Skill 激活限制测试
 */
class RunContextTest {

    private RunContext context;

    @BeforeEach
    void setUp() {
        context = RunContext.builder()
                .runId("test-run-123")
                .sessionId("test-session")
                .startTime(java.time.Instant.now())
                .config(LoopConfig.builder().build())
                .cancellationManager(new CancellationManager())
                .build();
    }

    @Test
    @DisplayName("Initial skill activation count should be 0")
    void testInitialActivationCount() {
        assertFalse(context.isSkillActivationLimitReached("skill_a"));
    }

    @Test
    @DisplayName("First activation should return count 1")
    void testFirstActivation() {
        int count = context.incrementSkillActivation("skill_a");

        assertEquals(1, count);
        assertFalse(context.isSkillActivationLimitReached("skill_a"));
    }

    @Test
    @DisplayName("Should reach limit after 3 activations")
    void testActivationLimit() {
        // 激活 3 次
        context.incrementSkillActivation("skill_a");
        context.incrementSkillActivation("skill_a");
        int thirdCount = context.incrementSkillActivation("skill_a");

        assertEquals(3, thirdCount);
        assertTrue(context.isSkillActivationLimitReached("skill_a"));
    }

    @Test
    @DisplayName("Different skills should have independent counts")
    void testIndependentCounts() {
        context.incrementSkillActivation("skill_a");
        context.incrementSkillActivation("skill_a");
        context.incrementSkillActivation("skill_a");

        // skill_a 达到限制
        assertTrue(context.isSkillActivationLimitReached("skill_a"));

        // skill_b 未达到限制
        assertFalse(context.isSkillActivationLimitReached("skill_b"));
        assertEquals(1, context.incrementSkillActivation("skill_b"));
    }

    @Test
    @DisplayName("Should not exceed limit even with more activations")
    void testExceedLimit() {
        // 激活 5 次
        for (int i = 0; i < 5; i++) {
            context.incrementSkillActivation("skill_a");
        }

        // 仍然显示达到限制
        assertTrue(context.isSkillActivationLimitReached("skill_a"));
    }
}
