package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanEngine Tests")
class PlanEngineTest {

    @Mock
    private StructuredOutputExecutor structuredOutputExecutor;

    @Test
    @DisplayName("should create initial plan from structured planner")
    void shouldCreateInitialPlanFromStructuredPlanner() {
        PlanEngine engine = new PlanEngine(structuredOutputExecutor);
        RunContext context = RunContext.create("run-1", "conn-1", "session-1", LoopConfig.builder().build(), new CancellationManager());
        context.setOriginalInput("打开浏览器访问知乎并检查页面加载");

        when(structuredOutputExecutor.execute(any(), eq(PlanEngine.PlanDraft.class))).thenReturn(
                StructuredLlmResult.<PlanEngine.PlanDraft>builder()
                        .value(PlanEngine.PlanDraft.builder()
                                .items(List.of(
                                        PlanEngine.PlanDraftItem.builder().title("打开浏览器访问知乎").useSubagent(false).build(),
                                        PlanEngine.PlanDraftItem.builder().title("检查页面加载结果").useSubagent(false).build()
                                ))
                                .build())
                        .build()
        );

        ExecutionPlan plan = engine.createInitialPlan(context, List.of());

        assertEquals(2, plan.getItems().size());
        assertEquals("item-1", plan.getCurrentItemId());
        assertEquals(PlanItemStatus.IN_PROGRESS, plan.getItems().get(0).getStatus());
        assertEquals(PlanItemStatus.PENDING, plan.getItems().get(1).getStatus());
    }

    @Test
    @DisplayName("should fallback to single item plan when planner fails")
    void shouldFallbackToSingleItemPlanWhenPlannerFails() {
        PlanEngine engine = new PlanEngine(structuredOutputExecutor);
        RunContext context = RunContext.create("run-2", "conn-2", "session-2", LoopConfig.builder().build(), new CancellationManager());
        context.setOriginalInput("导出 PDF");

        when(structuredOutputExecutor.execute(any(), eq(PlanEngine.PlanDraft.class)))
                .thenThrow(new RuntimeException("planner down"));

        ExecutionPlan plan = engine.createInitialPlan(context, List.of());

        assertEquals(1, plan.getItems().size());
        assertEquals("item-1", plan.getCurrentItemId());
        assertEquals("导出 PDF", plan.getItems().get(0).getTitle());
    }

    @Test
    @DisplayName("should advance to next pending item after current item is done")
    void shouldAdvanceToNextPendingItemAfterCurrentItemIsDone() {
        PlanEngine engine = new PlanEngine(structuredOutputExecutor);
        ExecutionPlan plan = ExecutionPlan.builder()
                .goal("test")
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(List.of(
                        PlanItem.builder().id("item-1").title("step 1").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).build(),
                        PlanItem.builder().id("item-2").title("step 2").status(PlanItemStatus.PENDING).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                )))
                .revision(1)
                .build();

        engine.markDone(plan, "item-1", "done");
        engine.advance(plan);

        assertEquals(PlanItemStatus.DONE, plan.getItems().get(0).getStatus());
        assertEquals("item-2", plan.getCurrentItemId());
        assertEquals(PlanItemStatus.IN_PROGRESS, plan.getItems().get(1).getStatus());
    }

    @Test
    @DisplayName("should mark plan completed when all items are done")
    void shouldMarkPlanCompletedWhenAllItemsAreDone() {
        PlanEngine engine = new PlanEngine(structuredOutputExecutor);
        ExecutionPlan plan = ExecutionPlan.builder()
                .goal("test")
                .status(ExecutionPlanStatus.ACTIVE)
                .currentItemId("item-1")
                .items(new java.util.ArrayList<>(List.of(
                        PlanItem.builder().id("item-1").title("step 1").status(PlanItemStatus.IN_PROGRESS).executionMode(PlanExecutionMode.MAIN_AGENT).build()
                )))
                .revision(1)
                .build();

        engine.markDone(plan, "item-1", null);
        engine.advance(plan);

        assertEquals(ExecutionPlanStatus.COMPLETED, plan.getStatus());
        assertNull(plan.getCurrentItemId());
        assertTrue(plan.allItemsDone());
    }
}
