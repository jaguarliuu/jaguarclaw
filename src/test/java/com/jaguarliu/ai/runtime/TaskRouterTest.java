package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.StructuredOutputExecutor;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskRouter Tests")
class TaskRouterTest {

    @Mock
    private StructuredOutputExecutor structuredOutputExecutor;

    @Test
    @DisplayName("should route greeting to direct via structured semantic router")
    void shouldRouteGreetingToDirectViaStructuredSemanticRouter() {
        TaskRouter router = new TaskRouter(structuredOutputExecutor, new PolicySupervisor());
        when(structuredOutputExecutor.execute(any(), eq(TaskRoutingDecision.class))).thenReturn(
                StructuredLlmResult.<TaskRoutingDecision>builder()
                        .value(TaskRoutingDecision.builder()
                                .routeMode(TaskRouteMode.DIRECT)
                                .complexity(TaskComplexity.DIRECT)
                                .reason("greeting")
                                .confidence(0.99)
                                .build())
                        .build()
        );

        TaskRoutingDecision decision = router.route("hi", List.of(), false, null);

        assertEquals(TaskRouteMode.DIRECT, decision.getRouteMode());
    }

    @Test
    @DisplayName("should route image input to react without semantic call")
    void shouldRouteImageInputToReactWithoutSemanticCall() {
        TaskRouter router = new TaskRouter(structuredOutputExecutor, new PolicySupervisor());

        TaskRoutingDecision decision = router.route("describe this image", List.of(), true, null);

        assertEquals(TaskRouteMode.REACT, decision.getRouteMode());
        assertEquals(TaskComplexity.HEAVY, decision.getComplexity());
    }

    @Test
    @DisplayName("should include model selection in router request")
    void shouldIncludeModelSelectionInRouterRequest() {
        TaskRouter router = new TaskRouter(structuredOutputExecutor, new PolicySupervisor());
        when(structuredOutputExecutor.execute(any(), eq(TaskRoutingDecision.class))).thenReturn(
                StructuredLlmResult.<TaskRoutingDecision>builder()
                        .value(TaskRoutingDecision.builder()
                                .routeMode(TaskRouteMode.DIRECT)
                                .complexity(TaskComplexity.DIRECT)
                                .build())
                        .build()
        );

        router.route("今天几号？", List.of(), false, "dashscope:qwen-plus");

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(structuredOutputExecutor).execute(captor.capture(), eq(TaskRoutingDecision.class));
        assertEquals("dashscope", captor.getValue().getProviderId());
        assertEquals("qwen-plus", captor.getValue().getModel());
    }

    @Test
    @DisplayName("should degrade to react fallback when semantic router fails")
    void shouldDegradeToReactFallbackWhenSemanticRouterFails() {
        TaskRouter router = new TaskRouter(structuredOutputExecutor, new PolicySupervisor());
        when(structuredOutputExecutor.execute(any(), eq(TaskRoutingDecision.class)))
                .thenThrow(new RuntimeException("llm down"));

        TaskRoutingDecision decision = router.route("hello", List.of(), false, null);

        assertEquals(TaskRouteMode.REACT, decision.getRouteMode());
    }
}
