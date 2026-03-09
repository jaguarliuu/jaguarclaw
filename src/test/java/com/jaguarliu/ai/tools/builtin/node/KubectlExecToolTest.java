package com.jaguarliu.ai.tools.builtin.node;

import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KubectlExecTool Tests")
class KubectlExecToolTest {

    @Test
    @DisplayName("blocked kubectl command should expose policy block")
    void blockedKubectlCommandShouldExposePolicyBlock() {
        NodeService nodeService = mock(NodeService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        RemoteCommandClassifier classifier = mock(RemoteCommandClassifier.class);
        when(nodeService.getSafetyPolicy(anyString())).thenReturn("strict");
        when(classifier.classify(anyString(), anyString())).thenReturn(
                new RemoteCommandClassifier.Classification(
                        RemoteCommandClassifier.SafetyLevel.DESTRUCTIVE,
                        "blocked by policy",
                        "strict"
                )
        );

        KubectlExecTool tool = new KubectlExecTool(nodeService, classifier, auditLogService);

        ToolResult result = tool.execute(Map.of(
                "alias", "k8s-prod",
                "command", "delete pod demo"
        )).block();

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(RuntimeFailureCategories.POLICY_BLOCK, result.getFailureCategory());
    }
}
