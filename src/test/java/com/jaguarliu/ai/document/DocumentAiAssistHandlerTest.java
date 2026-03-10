package com.jaguarliu.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.handler.agent.AgentRunHandler;
import com.jaguarliu.ai.gateway.rpc.handler.document.DocumentAiAssistHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.document.DocumentEntity;
import com.jaguarliu.ai.storage.entity.SessionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentAiAssistHandlerTest {

    @Mock AgentRunHandler agentRunHandler;
    @Mock DocumentService documentService;
    @Mock SessionService sessionService;
    @Mock ConnectionManager connectionManager;
    @Spy  ObjectMapper objectMapper;
    @InjectMocks DocumentAiAssistHandler handler;

    @Test
    void delegatesToAgentRunHandler() {
        var session = new SessionEntity();
        session.setId("sess-1");
        var doc = DocumentEntity.builder().id("doc-1").title("T").content("{}").ownerId("local-default").build();

        when(documentService.get("doc-1", "local-default")).thenReturn(doc);
        when(sessionService.findOrCreateDocumentSession("doc-1", "local-default")).thenReturn(session);
        when(agentRunHandler.handle(any(), any()))
                .thenReturn(Mono.just(RpcResponse.success("x", Map.of("runId", "run-42"))));

        var req = RpcRequest.builder()
                .id("req-1").method("document.ai.assist")
                .payload(Map.of("docId", "doc-1", "action", "optimize"))
                .build();

        var resp = handler.handle("conn-1", req).block();

        assertThat(resp).isNotNull();
        assertThat(resp.getError()).isNull();
        verify(agentRunHandler).handle(eq("conn-1"), argThat(r ->
                r.getPayload() instanceof Map<?,?> m && m.containsKey("sessionId")));
    }

    @Test
    void returnsErrorIfDocIdMissing() {
        var req = RpcRequest.builder()
                .id("req-2").method("document.ai.assist")
                .payload(Map.of("action", "optimize"))
                .build();
        var resp = handler.handle("conn-1", req).block();
        assertThat(resp.getError()).isNotNull();
        verifyNoInteractions(agentRunHandler);
    }
}
