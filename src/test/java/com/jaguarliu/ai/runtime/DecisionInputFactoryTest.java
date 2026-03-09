package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DecisionInputFactory Tests")
class DecisionInputFactoryTest {

    private final DecisionInputFactory factory = new DecisionInputFactory();

    @Test
    @DisplayName("should build assistant step input from context snapshot")
    void shouldBuildAssistantStepInputFromContextSnapshot() {
        RunContext context = RunContext.create(
                "run-1", "conn-1", "session-1",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.recordFailure(RuntimeFailureCategories.TOOL_ERROR);
        context.recordEnvironmentRepairAttempt();
        context.clearRuntimeFailureCategories();

        DecisionInput input = factory.fromAssistantStep(context, "任务已完成");

        assertEquals("任务已完成", input.assistantReply());
        assertEquals(List.of(), input.observations());
        assertTrue(input.runtimeFailureCategories().isEmpty());
        assertEquals(0, input.currentStep());
        assertEquals(1, input.environmentRepairAttempts());
        assertFalse(input.hasToolCalls());
        assertFalse(input.hasPendingSubagents());
        assertNotNull(input.progressSnapshot());
    }

    @Test
    @DisplayName("should build tool round input from tool results")
    void shouldBuildToolRoundInputFromToolResults() {
        RunContext context = RunContext.create(
                "run-2", "conn-2", "session-2",
                LoopConfig.builder().build(),
                new CancellationManager()
        );

        List<ToolExecutor.ToolExecutionResult> results = List.of(
                new ToolExecutor.ToolExecutionResult(
                        "call-1",
                        ToolResult.error("command not found", RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT),
                        RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT
                ),
                new ToolExecutor.ToolExecutionResult(
                        "call-2",
                        ToolResult.success("ok"),
                        null
                )
        );

        DecisionInput input = factory.fromToolRound(context, results, Set.of("sub-1"));

        assertNull(input.assistantReply());
        assertEquals(List.of("Error: command not found", "ok"), input.observations());
        assertEquals(Set.of(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT), input.runtimeFailureCategories());
        assertEquals(0, input.currentStep());
        assertEquals(0, input.environmentRepairAttempts());
        assertTrue(input.hasToolCalls());
        assertTrue(input.hasPendingSubagents());
        assertNotNull(input.progressSnapshot());
    }

    @Test
    @DisplayName("should snapshot categories and progress at creation time")
    void shouldSnapshotCategoriesAndProgressAtCreationTime() {
        RunContext context = RunContext.create(
                "run-3", "conn-3", "session-3",
                LoopConfig.builder().build(),
                new CancellationManager()
        );
        context.replaceRuntimeFailureCategories(Set.of(RuntimeFailureCategories.TOOL_ERROR));
        context.recordFailure(RuntimeFailureCategories.TOOL_ERROR);

        DecisionInput input = factory.fromAssistantStep(context, "继续执行");

        context.replaceRuntimeFailureCategories(Set.of(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT));
        context.recordFailure(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT);
        context.recordEnvironmentRepairAttempt();

        assertEquals(Set.of(RuntimeFailureCategories.TOOL_ERROR), input.runtimeFailureCategories());
        assertEquals(RuntimeFailureCategories.TOOL_ERROR, input.progressSnapshot().lastFailureCategory());
        assertEquals(0, input.environmentRepairAttempts());
    }
}
