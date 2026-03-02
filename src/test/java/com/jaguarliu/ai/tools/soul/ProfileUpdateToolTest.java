package com.jaguarliu.ai.tools.soul;

import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileUpdateToolTest {

    @Test
    void execute_writesFullContentToProfileMd() {
        SoulConfigService svc = mock(SoulConfigService.class);
        ProfileUpdateTool tool = new ProfileUpdateTool(svc);

        String newContent = "# User Profile\n\n## Preferences\n- Address as: Alex\n";
        Map<String, Object> args = Map.of(
                "content", newContent,
                "reason", "learned user name"
        );

        ToolResult result = tool.execute(args).block();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        verify(svc, times(1)).writeProfileMd("main", newContent);
    }

    @Test
    void execute_missingContent_returnsError() {
        ProfileUpdateTool tool = new ProfileUpdateTool(mock(SoulConfigService.class));

        ToolResult r = tool.execute(Map.of()).block();
        assertNotNull(r);
        assertFalse(r.isSuccess());
    }
}
