package com.jaguarliu.ai.gateway.rpc.handler.tool;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
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
            return Mono.just(RpcResponse.error(request.getId(), "UNAUTHORIZED", "Missing authenticated principal"));
        }
        if (principal.getRoles() == null || !principal.getRoles().contains("local_admin")) {
            return Mono.just(RpcResponse.error(request.getId(), "PERMISSION_DENIED", "local_admin role is required"));
        }

        if (toolName == null || toolName.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing tool name"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) payload.getOrDefault("arguments", Map.of());

        if (toolDispatcher.requiresHitl(toolName, null, arguments)) {
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
                )));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Object payload) {
        if (payload instanceof Map) {
            return (Map<String, Object>) payload;
        }
        return Map.of();
    }
}
