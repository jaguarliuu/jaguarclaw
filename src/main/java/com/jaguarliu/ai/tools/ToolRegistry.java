package com.jaguarliu.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 * 管理所有可用工具，支持 Spring 自动发现
 *
 * 使用 SmartInitializingSingleton 在所有 singleton bean 完全初始化后再发现工具。
 * 这样可以避免 @PostConstruct 时机过早导致的传递式循环依赖问题：
 * ToolRegistry.init() → getBeansOfType(Tool) → SessionsSpawnTool → SubagentService → AgentRuntime → ToolRegistry
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry implements SmartInitializingSingleton {

    /**
     * Spring 应用上下文，用于动态发现 Tool Bean
     */
    private final ApplicationContext applicationContext;

    /**
     * 工具映射表：name → Tool
     */
    private final Map<String, Tool> registry = new ConcurrentHashMap<>();

    /**
     * 在所有 singleton bean 完全初始化后发现并注册工具
     *
     * SmartInitializingSingleton.afterSingletonsInstantiated() 在所有 bean 的
     * 构造函数和 @PostConstruct 都执行完毕后调用，确保所有 Tool bean 都已就绪。
     */
    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Tool> tools = applicationContext.getBeansOfType(Tool.class);
        for (Tool tool : tools.values()) {
            register(tool);
        }
        log.info("ToolRegistry initialized with {} tools: {}",
                registry.size(),
                registry.keySet());
    }

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        String name = tool.getName();
        if (registry.containsKey(name)) {
            log.warn("Tool already registered, overwriting: {}", name);
        }
        registry.put(name, tool);
        log.debug("Registered tool: {}", name);
    }

    /**
     * 获取工具
     */
    public Optional<Tool> get(String name) {
        Tool tool = registry.get(name);
        if (tool == null || !tool.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(tool);
    }

    /**
     * 列出所有工具定义（供 LLM Function Calling 使用）
     */
    public List<ToolDefinition> listDefinitions() {
        return listDefinitions(ToolVisibilityResolver.VisibilityRequest.builder().build());
    }

    /**
     * 转换为 OpenAI Function Calling 格式
     */
    public List<Map<String, Object>> toOpenAiTools() {
        return toOpenAiTools(ToolVisibilityResolver.VisibilityRequest.builder().build());
    }

    /**
     * 转换为 OpenAI Function Calling 格式（过滤版）
     * 只包含指定的工具
     *
     * @param allowedTools 允许的工具名称集合
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiTools(Set<String> allowedTools) {
        return toOpenAiTools(ToolVisibilityResolver.VisibilityRequest.builder()
                .strategyAllowedTools(allowedTools)
                .build());
    }

    /**
     * 转换为 OpenAI Function Calling 格式（排除指定 MCP 服务器）
     *
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiToolsExcludingServers(Set<String> excludedMcpServers) {
        return toOpenAiTools(ToolVisibilityResolver.VisibilityRequest.builder()
                .excludedMcpServers(excludedMcpServers)
                .build());
    }

    /**
     * 转换为 OpenAI Function Calling 格式（组合过滤：skill 白名单 + MCP 排除）
     *
     * @param allowedTools       允许的工具名称集合（null 表示不限制）
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合（null 表示不排除）
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiTools(Set<String> allowedTools, Set<String> excludedMcpServers) {
        return toOpenAiTools(ToolVisibilityResolver.VisibilityRequest.builder()
                .strategyAllowedTools(allowedTools)
                .excludedMcpServers(excludedMcpServers)
                .build());
    }

    /**
     * 转换为 OpenAI Function Calling 格式（多维可见性聚合版）
     */
    public List<Map<String, Object>> toOpenAiTools(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return orderedVisibleTools(visibilityRequest).stream()
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 列出所有工具定义（排除指定 MCP 服务器）
     * 供 SystemPromptBuilder 使用
     *
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @return 过滤后的工具定义列表
     */
    public List<ToolDefinition> listDefinitions(Set<String> excludedMcpServers) {
        return listDefinitions(ToolVisibilityResolver.VisibilityRequest.builder()
                .excludedMcpServers(excludedMcpServers)
                .build());
    }

    /**
     * 列出工具定义（多维可见性聚合版）
     */
    public List<ToolDefinition> listDefinitions(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return orderedVisibleTools(visibilityRequest).stream()
                .map(Tool::getDefinition)
                .toList();
    }

    /**
     * 列出统一工具目录（带分类、来源、作用域元数据）
     */
    public List<ToolCatalogEntry> listCatalog() {
        return listCatalog(ToolVisibilityResolver.VisibilityRequest.builder().build());
    }

    /**
     * 列出统一工具目录（多维可见性聚合版）
     */
    public List<ToolCatalogEntry> listCatalog(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return orderedVisibleTools(visibilityRequest).stream()
                .map(ToolCatalogEntry::from)
                .toList();
    }

    /**
     * 列出统一工具目录分组（按 category 聚合，保持稳定顺序）
     */
    public List<ToolCatalogGroup> listCatalogGroups() {
        return listCatalogGroups(ToolVisibilityResolver.VisibilityRequest.builder().build());
    }

    /**
     * 列出统一工具目录分组（多维可见性聚合版）
     */
    public List<ToolCatalogGroup> listCatalogGroups(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        Map<String, List<ToolCatalogEntry>> grouped = new LinkedHashMap<>();
        for (ToolCatalogEntry entry : listCatalog(visibilityRequest)) {
            grouped.computeIfAbsent(entry.category(), ignored -> new java.util.ArrayList<>()).add(entry);
        }

        return grouped.entrySet().stream()
                .map(e -> new ToolCatalogGroup(
                        e.getKey(),
                        ToolCatalogEntry.categoryLabel(e.getKey()),
                        e.getValue().isEmpty() ? 999 : e.getValue().get(0).categoryOrder(),
                        List.copyOf(e.getValue())
                ))
                .toList();
    }

    /**
     * 列出可见工具名称（用于工具执行层白名单校验）
     */
    public Set<String> listVisibleToolNames(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return resolveVisibility(visibilityRequest).toolNames();
    }

    /**
     * 检查工具是否存在
     */
    public boolean exists(String name) {
        return get(name).isPresent();
    }

    /**
     * 获取已注册工具数量
     */
    public int size() {
        return (int) enabledTools().count();
    }

    // ==================== 渐进式加载方法 ====================

    /**
     * 转换为 OpenAI 格式（渐进式加载）
     * 
     * @param l1ToolIds 需要升级到 L1 的工具 ID（其他工具用 L0）
     * @return 工具列表
     */
    public List<Map<String, Object>> toOpenAiToolsProgressive(Set<String> l1ToolIds) {
        return orderedVisibleTools(ToolVisibilityResolver.VisibilityRequest.builder().build()).stream()
                .map(tool -> {
                    ToolDefinition def = tool.getDefinition();
                    if (l1ToolIds != null && l1ToolIds.contains(tool.getName())) {
                        return def.toOpenAiFormatL1();
                    }
                    return def.toOpenAiFormatL0();
                })
                .toList();
    }

    /**
     * 转换为 OpenAI 格式（渐进式加载 + 过滤）
     * 
     * @param allowedTools 允许的工具名称集合
     * @param excludedMcpServers 要排除的 MCP 服务器
     * @param l1ToolIds 需要升级到 L1 的工具 ID
     * @return 工具列表
     */
    public List<Map<String, Object>> toOpenAiToolsProgressive(
            Set<String> allowedTools,
            Set<String> excludedMcpServers,
            Set<String> l1ToolIds
    ) {
        ToolVisibilityResolver.VisibilityRequest request = ToolVisibilityResolver.VisibilityRequest.builder()
                .strategyAllowedTools(allowedTools)
                .excludedMcpServers(excludedMcpServers)
                .build();
        return orderedVisibleTools(request).stream()
                .map(tool -> {
                    ToolDefinition def = tool.getDefinition();
                    if (l1ToolIds != null && l1ToolIds.contains(tool.getName())) {
                        return def.toOpenAiFormatL1();
                    }
                    return def.toOpenAiFormatL0();
                })
                .toList();
    }

    /**
     * 估算当前工具的 L0 总 token 数
     */
    public int estimateL0Tokens() {
        return enabledTools()
                .mapToInt(tool -> tool.getDefinition().estimateL0Tokens())
                .sum();
    }

    /**
     * 估算给定 L1 工具集合的总 token 数
     */
    public int estimateProgressiveTokens(Set<String> l1ToolIds) {
        return enabledTools()
                .mapToInt(tool -> {
                    ToolDefinition def = tool.getDefinition();
                    if (l1ToolIds != null && l1ToolIds.contains(tool.getName())) {
                        return def.estimateL1Tokens();
                    }
                    return def.estimateL0Tokens();
                })
                .sum();
    }

    private java.util.stream.Stream<Tool> enabledTools() {
        return registry.values().stream().filter(Tool::isEnabled);
    }

    private ToolVisibilityResolver.VisibilityResult resolveVisibility(
            ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return ToolVisibilityResolver.resolve(
                enabledTools().toList(),
                visibilityRequest
        );
    }

    private List<Tool> orderedVisibleTools(ToolVisibilityResolver.VisibilityRequest visibilityRequest) {
        return resolveVisibility(visibilityRequest).tools().stream()
                .sorted(ToolCatalogEntry.toolOrder())
                .toList();
    }
}
