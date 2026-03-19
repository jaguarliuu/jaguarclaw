package com.jaguarliu.ai.tools.builtin.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.nodeconsole.NodeEntity;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * node_status_check 工具
 * 支持按别名或 tag 批量巡检节点状态，并可执行状态命令
 */
@Component
@RequiredArgsConstructor
public class NodeStatusCheckTool implements Tool {

    private static final int DEFAULT_MAX_NODES = 20;

    private final NodeService nodeService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("node_status_check")
                .description("检查节点连通性和运行状态。可按 alias 精确检查单节点，或按 tag/type 批量检查。可指定 command，否则按类型使用默认状态命令。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "alias", Map.of(
                                        "type", "string",
                                        "description", "节点别名（精确匹配，优先于 tag/type）"
                                ),
                                "tag", Map.of(
                                        "type", "string",
                                        "description", "按标签过滤（逗号标签中的单个值）"
                                ),
                                "type", Map.of(
                                        "type", "string",
                                        "description", "按连接器类型过滤",
                                        "enum", List.of("ssh", "k8s")
                                ),
                                "command", Map.of(
                                        "type", "string",
                                        "description", "连通后执行的状态命令（可选）"
                                ),
                                "max_nodes", Map.of(
                                        "type", "integer",
                                        "description", "批量检查最大节点数（默认 20）"
                                )
                        ),
                        "required", List.of()
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String alias = asString(arguments.get("alias"));
            String tag = asString(arguments.get("tag"));
            String type = asString(arguments.get("type"));
            String command = asString(arguments.get("command"));
            int maxNodes = asInt(arguments.get("max_nodes"), DEFAULT_MAX_NODES);

            List<NodeEntity> candidates = resolveCandidates(alias, tag, type, maxNodes);
            if (candidates.isEmpty()) {
                return ToolResult.error("No nodes matched the filter");
            }

            List<Map<String, Object>> reports = new ArrayList<>();
            for (NodeEntity node : candidates) {
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("alias", node.getAlias());
                report.put("type", node.getConnectorType());
                report.put("tags", node.getTags());

                NodeService.ConnectionTestReport test = nodeService.testConnectionDetailed(node.getId());
                report.put("connectionOk", test.success());
                report.put("errorType", test.errorType());
                report.put("message", test.message());
                report.put("durationMs", test.durationMs());
                report.put("testedAt", test.testedAt() != null ? test.testedAt().toString() : null);

                String commandToRun = (command != null && !command.isBlank())
                        ? command
                        : defaultStatusCommand(node.getConnectorType());
                if (test.success() && commandToRun != null) {
                    report.put("command", commandToRun);
                    try {
                        report.put("commandOk", true);
                        report.put("output", nodeService.executeCommand(node.getAlias(), commandToRun));
                    } catch (Exception e) {
                        report.put("commandOk", false);
                        report.put("output", e.getMessage());
                    }
                }

                reports.add(report);
            }

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "total", reports.size(),
                    "reports", reports
            ));
            return ToolResult.success(json);
        });
    }

    private List<NodeEntity> resolveCandidates(String alias, String tag, String type, int maxNodes) {
        if (alias != null && !alias.isBlank()) {
            return nodeService.findByAlias(alias).map(List::of).orElse(List.of());
        }

        return nodeService.listAll().stream()
                .filter(n -> type == null || type.isBlank() || type.equalsIgnoreCase(n.getConnectorType()))
                .filter(n -> tag == null || tag.isBlank() || hasTag(n.getTags(), tag))
                .limit(Math.max(1, maxNodes))
                .toList();
    }

    private static boolean hasTag(String tags, String expectedTag) {
        if (tags == null || tags.isBlank() || expectedTag == null || expectedTag.isBlank()) {
            return false;
        }
        String needle = expectedTag.trim().toLowerCase(Locale.ROOT);
        for (String token : tags.split(",")) {
            if (needle.equals(token.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String defaultStatusCommand(String connectorType) {
        if ("ssh".equalsIgnoreCase(connectorType)) {
            return "uptime";
        }
        if ("k8s".equalsIgnoreCase(connectorType)) {
            return "get nodes";
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }
}
