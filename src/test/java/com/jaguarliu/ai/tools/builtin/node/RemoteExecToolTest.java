package com.jaguarliu.ai.tools.builtin.node;

import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RemoteExecTool Tests")
class RemoteExecToolTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("invalid script path should be repairable environment")
    void invalidScriptPathShouldBeRepairableEnvironment() {
        NodeService nodeService = mock(NodeService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RemoteCommandClassifier classifier = mock(RemoteCommandClassifier.class);
        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(tempDir.toString());

        RemoteExecTool tool = new RemoteExecTool(nodeService, classifier, auditLogService, properties);

        ToolResult result = tool.execute(Map.of(
                "alias", "n1",
                "script_path", "missing-script.sh"
        )).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT, result.getFailureCategory());
    }

    @Test
    @DisplayName("blocked remote command should expose policy block")
    void blockedRemoteCommandShouldExposePolicyBlock() {
        NodeService nodeService = mock(NodeService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RemoteCommandClassifier classifier = mock(RemoteCommandClassifier.class);
        ToolsProperties properties = new ToolsProperties();
        properties.setWorkspace(tempDir.toString());
        when(nodeService.getSafetyPolicy(anyString())).thenReturn("strict");
        when(classifier.classify(anyString(), anyString())).thenReturn(
                new RemoteCommandClassifier.Classification(
                        RemoteCommandClassifier.SafetyLevel.DESTRUCTIVE,
                        "blocked by policy",
                        "strict"
                )
        );

        RemoteExecTool tool = new RemoteExecTool(nodeService, classifier, auditLogService, properties);

        ToolResult result = tool.execute(Map.of(
                "alias", "n1",
                "command", "rm -rf /tmp/demo"
        )).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(RuntimeFailureCategories.POLICY_BLOCK, result.getFailureCategory());
    }
}
