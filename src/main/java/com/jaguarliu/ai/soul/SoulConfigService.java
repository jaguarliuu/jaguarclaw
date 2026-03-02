package com.jaguarliu.ai.soul;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Soul 配置服务（Agent 作用域）
 * 文件存储在：{workspace}/agents/{agentId}/soul.json 与 SOUL.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulConfigService {

    private final ObjectMapper objectMapper;
    private final AgentWorkspaceResolver workspaceResolver;

    private static final String SOUL_JSON_FILE = "soul.json";
    private static final String SOUL_MD_FILE = "SOUL.md";

    /**
     * 获取配置 Map；文件不存在时返回默认值
     */
    public Map<String, Object> getConfig() {
        return getConfig("main");
    }

    /**
     * 获取指定 agent 的配置 Map；文件不存在时返回默认值
     */
    public Map<String, Object> getConfig(String agentId) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Path path = soulJsonPath(resolvedAgentId);
        if (!Files.exists(path)) {
            return defaultConfig(resolvedAgentId);
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.warn("Failed to read soul.json, returning defaults", e);
            return defaultConfig(resolvedAgentId);
        }
    }

    /**
     * 持久化配置到 soul.json
     */
    public void saveConfig(Map<String, Object> configMap) {
        saveConfig("main", configMap);
    }

    /**
     * 持久化指定 agent 配置到 soul.json，并同步更新 SOUL.md
     */
    public void saveConfig(String agentId, Map<String, Object> configMap) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Path path = soulJsonPath(resolvedAgentId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), configMap);
            syncSoulMarkdown(resolvedAgentId);
            log.info("Soul config saved: agentId={}, agentName={}", resolvedAgentId, configMap.get("agentName"));
        } catch (IOException e) {
            log.error("Failed to save soul.json", e);
            throw new RuntimeException("Failed to save soul config", e);
        }
    }

    /**
     * 生成系统提示词片段
     */
    public String generateSystemPrompt() {
        return generateSystemPrompt("main");
    }

    /**
     * 生成指定 agent 的系统提示词片段
     */
    public String generateSystemPrompt(String agentId) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Map<String, Object> soul = getConfig(resolvedAgentId);
        if (Boolean.FALSE.equals(soul.get("enabled"))) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("# Your Identity\n\n");

        String agentName = (String) soul.get("agentName");
        if (agentName != null && !agentName.isEmpty()) {
            prompt.append("Your name is ").append(agentName).append(".\n\n");
        }

        String personality = (String) soul.get("personality");
        if (personality != null && !personality.isEmpty()) {
            prompt.append("## Personality\n");
            prompt.append(personality).append("\n\n");
        }

        List<String> traits = toStringList(soul.get("traits"));
        if (!traits.isEmpty()) {
            prompt.append("## Key Traits\n");
            traits.forEach(trait -> prompt.append("- ").append(trait).append("\n"));
            prompt.append("\n");
        }

        List<String> expertise = toStringList(soul.get("expertise"));
        if (!expertise.isEmpty()) {
            prompt.append("## Areas of Expertise\n");
            expertise.forEach(area -> prompt.append("- ").append(area).append("\n"));
            prompt.append("\n");
        }

        String responseStyle = (String) soul.get("responseStyle");
        if (responseStyle != null) {
            prompt.append("## Response Style\n");
            prompt.append("Tone: ").append(responseStyle).append("\n");
        }

        String detailLevel = (String) soul.get("detailLevel");
        if (detailLevel != null) {
            prompt.append("Detail Level: ").append(detailLevel).append("\n\n");
        }

        List<String> forbidden = toStringList(soul.get("forbiddenTopics"));
        if (!forbidden.isEmpty()) {
            prompt.append("## Topics to Avoid\n");
            forbidden.forEach(topic -> prompt.append("- ").append(topic).append("\n"));
            prompt.append("\n");
        }

        String customPrompt = (String) soul.get("customPrompt");
        if (customPrompt != null && !customPrompt.isEmpty()) {
            prompt.append("## Additional Guidelines\n");
            prompt.append(customPrompt).append("\n\n");
        }

        prompt.append("## Self-Improvement\n");
        prompt.append("You can update your own behavior using the update_soul tool.\n");
        prompt.append("Use it when users give style feedback, you learn preferences, or want to remember important context.\n\n");

        return prompt.toString();
    }

    public void ensureAgentDefaults(String agentId) {
        ensureAgentDefaults(agentId, null);
    }

    /**
     * 确保指定 agent 的 soul 文件已初始化，displayName 用于设置默认 agentName
     */
    public void ensureAgentDefaults(String agentId, String displayName) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Path jsonPath = soulJsonPath(resolvedAgentId);
        if (!Files.exists(jsonPath)) {
            Map<String, Object> config = defaultConfig(resolvedAgentId);
            if (displayName != null && !displayName.isBlank()) {
                config.put("agentName", displayName);
            }
            saveConfig(resolvedAgentId, config);
            log.info("Initialized default soul.json at {}", jsonPath);
            return;
        }
        Path mdPath = soulMdPath(resolvedAgentId);
        if (!Files.exists(mdPath)) {
            syncSoulMarkdown(resolvedAgentId);
            log.info("Initialized default SOUL.md at {}", mdPath);
        }
    }

    private void syncSoulMarkdown(String agentId) {
        Path path = soulMdPath(agentId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, generateSystemPrompt(agentId));
        } catch (IOException e) {
            log.warn("Failed to sync SOUL.md for agentId={}", agentId, e);
        }
    }

    private Path soulJsonPath(String agentId) {
        return workspaceResolver.resolveAgentFile(agentId, SOUL_JSON_FILE);
    }

    private Path soulMdPath(String agentId) {
        return workspaceResolver.resolveAgentFile(agentId, SOUL_MD_FILE);
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        return Collections.emptyList();
    }

    private Map<String, Object> defaultConfig(String agentId) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("agentName", "main".equals(agentId) ? "JaguarClaw" : agentId);
        config.put("personality", "A helpful and professional AI assistant");
        config.put("traits", Collections.emptyList());
        config.put("responseStyle", "balanced");
        config.put("detailLevel", "balanced");
        config.put("expertise", Collections.emptyList());
        config.put("forbiddenTopics", Collections.emptyList());
        config.put("customPrompt", "");
        config.put("enabled", true);
        return config;
    }
}
