package com.jaguarliu.ai.tools;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一工具目录条目（用于工具分组、排序、展示）
 */
public record ToolCatalogEntry(
        String name,
        String description,
        boolean hitl,
        String category,
        int categoryOrder,
        String source,
        String scope,
        String mcpServer,
        String riskLevel,
        List<String> tags
) {

    private static final Set<String> DATA_TOOLS = Set.of(
            "datasource_query", "list_tables", "get_table_schema", "sample_data"
    );

    private static final Set<String> REMOTE_TOOLS = Set.of(
            "remote_exec", "kubectl_exec", "node_list", "node_status_check"
    );

    private static final Set<String> NETWORK_TOOLS = Set.of(
            "http_get", "web_search", "ping"
    );

    private static final Set<String> WORKFLOW_TOOLS = Set.of(
            "sessions_spawn", "create_schedule", "use_skill"
    );

    private static final Set<String> DELIVERY_TOOLS = Set.of(
            "send_email", "send_webhook"
    );

    public static final Comparator<ToolCatalogEntry> ORDER =
            Comparator.comparingInt(ToolCatalogEntry::categoryOrder)
                    .thenComparing(ToolCatalogEntry::source)
                    .thenComparing(ToolCatalogEntry::name);

    public static Comparator<Tool> toolOrder() {
        return (a, b) -> ORDER.compare(from(a), from(b));
    }

    public static ToolCatalogEntry from(Tool tool) {
        ToolDefinition def = tool.getDefinition();

        String source = inferSource(tool);
        String scope = inferScope(tool);
        CategoryInfo categoryInfo = resolveCategory(def.getName(), source);

        return new ToolCatalogEntry(
                def.getName(),
                def.getDescription(),
                def.isHitl(),
                categoryInfo.key(),
                categoryInfo.order(),
                source,
                scope,
                tool.getMcpServerName(),
                def.getRiskLevel() != null ? def.getRiskLevel() : "low",
                def.getTags() != null ? def.getTags() : List.of()
        );
    }

    public static String categoryLabel(String category) {
        return switch (category) {
            case "filesystem" -> "Filesystem";
            case "command_local" -> "Local Command";
            case "command_remote" -> "Remote Command";
            case "memory" -> "Memory";
            case "datasource" -> "Data Source";
            case "network" -> "Network";
            case "workflow" -> "Workflow";
            case "delivery" -> "Delivery";
            case "profile" -> "Profile";
            case "skill" -> "Skill";
            case "mcp" -> "MCP";
            default -> "Other";
        };
    }

    private static String inferSource(Tool tool) {
        if (tool instanceof ToolVisibilityResolver.ScopedToolMetadataProvider provider) {
            ToolVisibilityResolver.ToolDomain domain = provider.getToolDomain();
            if (domain != null) {
                return domain.name().toLowerCase(Locale.ROOT);
            }
        }
        return tool.getMcpServerName() != null ? "mcp" : "builtin";
    }

    private static String inferScope(Tool tool) {
        if (tool instanceof ToolVisibilityResolver.ScopedToolMetadataProvider provider) {
            ToolVisibilityResolver.ToolScope scope = provider.getToolScope();
            if (scope != null) {
                return scope.name().toLowerCase(Locale.ROOT);
            }
        }
        return "global";
    }

    private static CategoryInfo resolveCategory(String name, String source) {
        if ("mcp".equals(source)) {
            return new CategoryInfo("mcp", 900);
        }
        if ("skill".equals(source)) {
            return new CategoryInfo("skill", 800);
        }
        if (name == null || name.isBlank()) {
            return new CategoryInfo("other", 999);
        }

        if ("read_file".equals(name) || "write_file".equals(name)) {
            return new CategoryInfo("filesystem", 10);
        }
        if (name.startsWith("shell")) {
            return new CategoryInfo("command_local", 20);
        }
        if (REMOTE_TOOLS.contains(name) || name.startsWith("node_")) {
            return new CategoryInfo("command_remote", 30);
        }
        if (name.startsWith("memory_")) {
            return new CategoryInfo("memory", 40);
        }
        if (DATA_TOOLS.contains(name)) {
            return new CategoryInfo("datasource", 50);
        }
        if (NETWORK_TOOLS.contains(name)) {
            return new CategoryInfo("network", 60);
        }
        if (WORKFLOW_TOOLS.contains(name)) {
            return new CategoryInfo("workflow", 70);
        }
        if (DELIVERY_TOOLS.contains(name)) {
            return new CategoryInfo("delivery", 80);
        }
        if (name.startsWith("update_")) {
            return new CategoryInfo("profile", 90);
        }
        return new CategoryInfo("other", 999);
    }

    public Map<String, Object> toSimpleDto() {
        java.util.LinkedHashMap<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("name", name);
        dto.put("description", description);
        dto.put("hitl", hitl);
        dto.put("category", category);
        dto.put("source", source);
        dto.put("scope", scope);
        dto.put("riskLevel", riskLevel);
        if (mcpServer != null && !mcpServer.isBlank()) {
            dto.put("mcpServer", mcpServer);
        }
        if (tags != null && !tags.isEmpty()) {
            dto.put("tags", tags);
        }
        return dto;
    }

    private record CategoryInfo(String key, int order) {
    }
}

