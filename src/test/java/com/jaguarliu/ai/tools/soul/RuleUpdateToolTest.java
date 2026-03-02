package com.jaguarliu.ai.tools.soul;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuleUpdateToolTest {

    @Test
    void execute_writesFullContentToRuleMd() {
        SoulConfigService svc = mock(SoulConfigService.class);
        RuleUpdateTool tool = new RuleUpdateTool(svc);

        String newContent = "# Rules\n\n- Be honest.\n";
        Map<String, Object> args = Map.of(
                "content", newContent,
                "reason", "adding honesty rule"
        );

        ToolResult result = tool.execute(args).block();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        verify(svc, times(1)).writeRuleMd("main", newContent);
    }

    @Test
    void execute_missingContent_returnsError() {
        RuleUpdateTool tool = new RuleUpdateTool(mock(SoulConfigService.class));

        ToolResult r = tool.execute(Map.of("reason", "no content")).block();
        assertNotNull(r);
        assertFalse(r.isSuccess());
    }
}
