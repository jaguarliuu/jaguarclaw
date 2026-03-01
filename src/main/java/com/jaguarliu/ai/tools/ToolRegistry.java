package com.jaguarliu.ai.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
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
        return enabledTools()
                .map(Tool::getDefinition)
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式
     */
    public List<Map<String, Object>> toOpenAiTools() {
        return enabledTools()
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式（过滤版）
     * 只包含指定的工具
     *
     * @param allowedTools 允许的工具名称集合
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiTools(Set<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return toOpenAiTools();
        }
        return enabledTools()
                .filter(tool -> allowedTools.contains(tool.getName()))
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式（排除指定 MCP 服务器）
     *
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiToolsExcludingServers(Set<String> excludedMcpServers) {
        if (excludedMcpServers == null || excludedMcpServers.isEmpty()) {
            return toOpenAiTools();
        }
        return enabledTools()
                .filter(tool -> {
                    String serverName = tool.getMcpServerName();
                    return serverName == null || !excludedMcpServers.contains(serverName);
                })
                .map(tool -> tool.getDefinition().toOpenAiFormat())
                .toList();
    }

    /**
     * 转换为 OpenAI Function Calling 格式（组合过滤：skill 白名单 + MCP 排除）
     *
     * @param allowedTools       允许的工具名称集合（null 表示不限制）
     * @param excludedMcpServers 要排除的 MCP 服务器名称集合（null 表示不排除）
     * @return 过滤后的工具列表
     */
    public List<Map<String, Object>> toOpenAiTools(Set<String> allowedTools, Set<String> excludedMcpServers) {
        if ((allowedTools == null || allowedTools.isEmpty())
                && (excludedMcpServers == null || excludedMcpServers.isEmpty())) {
            return toOpenAiTools();
        }
        return enabledTools()
                .filter(tool -> {
                    // skill 白名单过滤
                    if (allowedTools != null && !allowedTools.isEmpty()
                            && !allowedTools.contains(tool.getName())) {
                        return false;
                    }
                    // MCP 服务器排除过滤
                    if (excludedMcpServers != null && !excludedMcpServers.isEmpty()) {
                        String serverName = tool.getMcpServerName();
                        if (serverName != null && excludedMcpServers.contains(serverName)) {
                            return false;
                        }
                    }
                    return true;
                })
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
        if (excludedMcpServers == null || excludedMcpServers.isEmpty()) {
            return listDefinitions();
        }
        return enabledTools()
                .filter(tool -> {
                    String serverName = tool.getMcpServerName();
                    return serverName == null || !excludedMcpServers.contains(serverName);
                })
                .map(Tool::getDefinition)
                .toList();
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
        return enabledTools()
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
        return enabledTools()
                .filter(tool -> {
                    // 白名单过滤
                    if (allowedTools != null && !allowedTools.isEmpty()) {
                        if (!allowedTools.contains(tool.getName())) {
                            return false;
                        }
                    }
                    // MCP 服务器排除
                    if (excludedMcpServers != null && !excludedMcpServers.isEmpty()) {
                        String serverName = tool.getMcpServerName();
                        if (serverName != null && excludedMcpServers.contains(serverName)) {
                            return false;
                        }
                    }
                    return true;
                })
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
}
