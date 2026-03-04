package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AgentRuntime Tests")
class AgentRuntimeTest {

    @Test
    @DisplayName("should detect HITL rejection marker from tool result")
    void shouldDetectHitlRejectionMarker() {
        ToolResult result = ToolResult.error(ToolExecutor.HITL_REJECTED_MARKER + ": rejected");
        assertTrue(AgentRuntime.isHitlRejectedResult(result));
    }

    @Test
    @DisplayName("should not treat normal tool errors as HITL rejection")
    void shouldIgnoreGenericToolErrors() {
        ToolResult result = ToolResult.error("Command blocked by safety policy");
        assertFalse(AgentRuntime.isHitlRejectedResult(result));
    }
}

