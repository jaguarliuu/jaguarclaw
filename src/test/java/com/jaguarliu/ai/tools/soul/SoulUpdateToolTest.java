package com.jaguarliu.ai.tools.soul;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoulUpdateToolTest {

    @Test
    void execute_updatesSingleField_viaSaveConfig() {
        SoulConfigService svc = mock(SoulConfigService.class);
        SoulUpdateTool tool = new SoulUpdateTool(svc);

        Map<String, Object> current = new HashMap<>();
        current.put("agentName", "JaguarClaw");
        current.put("responseStyle", "balanced");
        current.put("traits", List.of());
        when(svc.getConfig()).thenReturn(current);

        Map<String, Object> args = Map.of(
                "field", "responseStyle",
                "value", "concise",
                "reason", "test"
        );

        ToolResult result = tool.execute(args).block();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        verify(svc, times(1)).saveConfig(argThat(map -> "concise".equals(map.get("responseStyle"))));
    }

    @Test
    void execute_validatesRequiredParameters() {
        SoulUpdateTool tool = new SoulUpdateTool(mock(SoulConfigService.class));

        ToolResult r1 = tool.execute(Map.of()).block();
        assertNotNull(r1);
        assertFalse(r1.isSuccess());

        ToolResult r2 = tool.execute(Map.of("field", "responseStyle")).block();
        assertNotNull(r2);
        assertFalse(r2.isSuccess());
    }
}

