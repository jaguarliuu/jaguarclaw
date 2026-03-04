package com.jaguarliu.ai.gateway.rpc.handler.tool;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.tools.ToolCatalogEntry;
import com.jaguarliu.ai.tools.ToolCatalogGroup;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * tool.list 处理器
 * 列出所有已注册的工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolListHandler implements RpcHandler {

    private final ToolRegistry toolRegistry;

    @Override
    public String getMethod() {
        return "tool.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        List<ToolCatalogEntry> catalog = toolRegistry.listCatalog();
        List<ToolCatalogGroup> groups = toolRegistry.listCatalogGroups();

        List<Map<String, Object>> toolDtos = catalog.stream()
                .map(ToolCatalogEntry::toSimpleDto)
                .toList();

        List<Map<String, Object>> groupDtos = groups.stream()
                .map(g -> Map.<String, Object>of(
                        "category", g.category(),
                        "label", g.label(),
                        "order", g.order(),
                        "tools", g.tools().stream().map(ToolCatalogEntry::toSimpleDto).toList()
                ))
                .toList();

        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "tools", toolDtos,
                "groups", groupDtos,
                "count", toolDtos.size(),
                "groupCount", groupDtos.size()
        )));
    }
}
