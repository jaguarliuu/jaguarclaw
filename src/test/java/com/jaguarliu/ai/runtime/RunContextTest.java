package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        assertTrue(context.isSkillActivationLimitReached("skill_a"));
        assertFalse(context.isSkillActivationLimitReached("skill_b"));
        assertEquals(1, context.incrementSkillActivation("skill_b"));
    }

    @Test
    @DisplayName("Should not exceed limit even with more activations")
    void testExceedLimit() {
        for (int i = 0; i < 5; i++) {
            context.incrementSkillActivation("skill_a");
        }

        assertTrue(context.isSkillActivationLimitReached("skill_a"));
    }

    @Test
    @DisplayName("addUsage accumulates across multiple calls")
    void addUsageAccumulatesAcrossMultipleCalls() {
        RunContext ctx = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        LlmResponse.Usage first = LlmResponse.Usage.builder()
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .cacheReadInputTokens(80)
                .cacheCreationInputTokens(0)
                .build();
        LlmResponse.Usage second = LlmResponse.Usage.builder()
                .promptTokens(200)
                .completionTokens(100)
                .totalTokens(300)
                .cacheReadInputTokens(160)
                .cacheCreationInputTokens(0)
                .build();

        ctx.addUsage(first);
        ctx.addUsage(second);

        assertEquals(300, ctx.getTotalInputTokens());
        assertEquals(150, ctx.getTotalOutputTokens());
        assertEquals(240, ctx.getTotalCacheReadTokens());
        assertEquals(450, ctx.getTotalTokens());
    }

    @Test
    @DisplayName("addUsage handles null fields gracefully")
    void addUsageHandlesNullFieldsGracefully() {
        RunContext ctx = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        LlmResponse.Usage usage = LlmResponse.Usage.builder()
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .build();

        assertDoesNotThrow(() -> ctx.addUsage(usage));
        assertEquals(100, ctx.getTotalInputTokens());
        assertEquals(50, ctx.getTotalOutputTokens());
        assertEquals(0, ctx.getTotalCacheReadTokens());
        assertEquals(150, ctx.getTotalTokens());
    }

    @Test
    @DisplayName("Should store terminal outcome in RunContext")
    void shouldStoreTerminalOutcomeInRunContext() {
        RunContext ctx = RunContext.create(
                "r1", "c1", "s1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        ctx.setOutcome(RunOutcome.blockedByEnvironment("missing wkhtmltopdf"));

        assertTrue(ctx.hasOutcome());
        assertEquals(RunOutcomeStatus.BLOCKED_BY_ENVIRONMENT, ctx.getOutcome().status());
    }
}
