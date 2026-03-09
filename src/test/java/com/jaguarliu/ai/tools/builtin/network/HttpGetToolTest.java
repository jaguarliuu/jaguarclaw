package com.jaguarliu.ai.tools.builtin.network;

import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("HttpGetTool Tests")
class HttpGetToolTest {

    @Test
    @DisplayName("untrusted domain should require user decision")
    void untrustedDomainShouldRequireUserDecision() {
        ToolConfigProperties properties = new ToolConfigProperties();
        HttpGetTool tool = new HttpGetTool(properties);

        ToolResult result = tool.execute(Map.of("url", "https://example.com")).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(RuntimeFailureCategories.USER_DECISION_REQUIRED, result.getFailureCategory());
    }
}
