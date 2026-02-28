package com.jaguarliu.ai.gateway.rpc.handler.tool;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.tools.ToolDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * tool.execute 处理器
 * 通过 ToolDispatcher 执行指定工具（测试用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecuteHandler implements RpcHandler {

    private final ToolDispatcher toolDispatcher;
    private final ConnectionManager connectionManager;
    private final AuditLogService auditLogService;

    @Override
    public String getMethod() {
        return "tool.execute";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = extractPayload(request.getPayload());
        String toolName = (String) payload.get("name");

        var principal = connectionManager.getPrincipal(connectionId);
        if (principal == null) {
            auditLogService.logSecurityEvent("ws.rpc.tool.unauthorized", null, getMethod(), "rejected",
                    "Missing authenticated principal", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal"));
        }
        if (principal.getRoles() == null || !principal.getRoles().contains("local_admin")) {
            auditLogService.logSecurityEvent("ws.rpc.tool.permission_denied", null, getMethod(), "rejected",
                    "local_admin role is required", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "PERMISSION_DENIED", "local_admin role is required"));
        }

        if (toolName == null || toolName.isBlank()) {
            auditLogService.logSecurityEvent("ws.rpc.tool.invalid_params", null, getMethod(), "rejected",
                    "Missing tool name", connectionId, request.getId());
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing tool name"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) payload.getOrDefault("arguments", Map.of());

        if (toolDispatcher.requiresHitl(toolName, null, arguments)) {
            auditLogService.logSecurityEvent("ws.rpc.tool.hitl_required", null, toolName, "rejected",
                    "Tool requires confirmation", connectionId, request.getId());
            return Mono.just(RpcResponse.error(
                    request.getId(),
                    "HITL_REQUIRED",
                    "Tool requires confirmation before execution: " + toolName
            ));
        }

        return toolDispatcher.dispatch(toolName, arguments)
                .map(result -> RpcResponse.success(request.getId(), Map.of(
                        "tool", toolName,
                        "success", result.isSuccess(),
                        "content", result.getContent()
                )))
                .doOnNext(resp -> auditLogService.logSecurityEvent(
                        "ws.rpc.tool.execute",
                        null,
                        toolName,
                        "success",
                        "Tool executed",
                        connectionId,
                        request.getId()
                ))
                .doOnError(error -> auditLogService.logSecurityEvent(
                        "ws.rpc.tool.execute_error",
                        null,
                        toolName,
                        "error",
                        error.getMessage(),
                        connectionId,
                        request.getId()
                ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return Map.of();
    }
}
