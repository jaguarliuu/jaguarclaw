package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadRouter workspace routing tests")
class FileUploadRouterTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private AgentWorkspaceResolver agentWorkspaceResolver;

    @Mock
    private SessionFileService sessionFileService;

    @Test
    @DisplayName("优先按 session 的 agent workspace 保存")
    void shouldResolveUploadRootFromSessionAgent() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace("./workspace-root");

        FileUploadRouter router = new FileUploadRouter(props, sessionService, agentWorkspaceResolver, sessionFileService);

        SessionEntity session = SessionEntity.builder().id("s-1").agentId("agent-a").build();
        when(sessionService.get("s-1")).thenReturn(Optional.of(session));
        when(agentWorkspaceResolver.normalizeAgentId("agent-a")).thenReturn("agent-a");
        Path agentWorkspace = Path.of("/tmp/workspace-agent-a");
        when(agentWorkspaceResolver.resolveAgentWorkspace("agent-a")).thenReturn(agentWorkspace);

        Path resolved = router.resolveUploadRoot("s-1", null);

        assertEquals(agentWorkspace, resolved);
        verify(sessionService).get("s-1");
        verify(agentWorkspaceResolver).normalizeAgentId("agent-a");
        verify(agentWorkspaceResolver).resolveAgentWorkspace("agent-a");
    }

    @Test
    @DisplayName("无 session 时按 agentId workspace 保存")
    void shouldResolveUploadRootFromAgentId() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace("./workspace-root");

        FileUploadRouter router = new FileUploadRouter(props, sessionService, agentWorkspaceResolver, sessionFileService);

        when(agentWorkspaceResolver.normalizeAgentId("agent-b")).thenReturn("agent-b");
        Path agentWorkspace = Path.of("/tmp/workspace-agent-b");
        when(agentWorkspaceResolver.resolveAgentWorkspace("agent-b")).thenReturn(agentWorkspace);

        Path resolved = router.resolveUploadRoot(null, "agent-b");

        assertEquals(agentWorkspace, resolved);
        verify(agentWorkspaceResolver).normalizeAgentId("agent-b");
        verify(agentWorkspaceResolver).resolveAgentWorkspace("agent-b");
    }

    @Test
    @DisplayName("无 session/agent 时回退到全局 workspace")
    void shouldFallbackToGlobalWorkspace() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace("./workspace-root");

        FileUploadRouter router = new FileUploadRouter(props, sessionService, agentWorkspaceResolver, sessionFileService);

        Path resolved = router.resolveUploadRoot(" ", " ");

        assertEquals(Path.of("./workspace-root").toAbsolutePath().normalize(), resolved);
        verifyNoInteractions(sessionService, agentWorkspaceResolver);
    }


    @Test
    @DisplayName("支持图片扩展名和 MIME 类型")
    void shouldAcceptImageExtensionsAndMimeType() {
        assertEquals(true, FileUploadRouter.isAllowedExtension(".png"));
        assertEquals(true, FileUploadRouter.isAllowedExtension(".jpg"));
        assertEquals("image/png", FileUploadRouter.detectMimeType("demo.png"));
        assertEquals("image/jpeg", FileUploadRouter.detectMimeType("demo.jpg"));
    }

    @Test
    @DisplayName("传入不存在的 sessionId 时抛出错误")
    void shouldRejectUnknownSessionId() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace("./workspace-root");

        FileUploadRouter router = new FileUploadRouter(props, sessionService, agentWorkspaceResolver, sessionFileService);

        when(sessionService.get("missing-session")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> router.resolveUploadRoot("missing-session", "agent-c"));

        verify(sessionService).get("missing-session");
        verifyNoInteractions(agentWorkspaceResolver);
    }
}
