package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.security.ConnectionPrincipal;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.runtime.CancellationManager;
import com.jaguarliu.ai.session.RunService;
import com.jaguarliu.ai.session.RunStatus;
import com.jaguarliu.ai.storage.entity.RunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCancelHandlerTest {

    @Mock
    private CancellationManager cancellationManager;

    @Mock
    private RunService runService;

    @Mock
    private ConnectionManager connectionManager;

    @InjectMocks
    private AgentCancelHandler handler;

    private static final String CONNECTION_ID = "conn-1";
    private static final String PRINCIPAL_ID = "principal-1";
    private static final String RUN_ID = "run-1";

    @BeforeEach
    void setUp() {
        when(connectionManager.getPrincipal(CONNECTION_ID))
                .thenReturn(ConnectionPrincipal.builder().principalId(PRINCIPAL_ID).build());
    }

    @Test
    void shouldMarkQueuedRunCanceledImmediately() {
        RunEntity queued = RunEntity.builder().id(RUN_ID).status(RunStatus.QUEUED.getValue()).build();
        when(runService.get(RUN_ID, PRINCIPAL_ID)).thenReturn(Optional.of(queued));

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("agent.cancel")
                .payload(Map.of("runId", RUN_ID))
                .build();

        RpcResponse response = handler.handle(CONNECTION_ID, request).block();

        assertNull(response.getError());
        verify(runService).updateStatus(RUN_ID, RunStatus.CANCELED);
        verify(cancellationManager, never()).requestCancel(any());
    }

    @Test
    void shouldRequestCancellationForRunningRun() {
        RunEntity running = RunEntity.builder().id(RUN_ID).status(RunStatus.RUNNING.getValue()).build();
        when(runService.get(RUN_ID, PRINCIPAL_ID)).thenReturn(Optional.of(running));

        RpcRequest request = RpcRequest.builder()
                .id("req-2")
                .method("agent.cancel")
                .payload(Map.of("runId", RUN_ID))
                .build();

        RpcResponse response = handler.handle(CONNECTION_ID, request).block();

        assertNull(response.getError());
        verify(cancellationManager).requestCancel(RUN_ID);
        verify(runService, never()).updateStatus(RUN_ID, RunStatus.CANCELED);
    }
}
