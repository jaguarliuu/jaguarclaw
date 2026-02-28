package com.jaguarliu.ai.soul;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Soul 配置服务 — 文件存储（{configDir}/soul.json）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulConfigService {

    @Value("${tools.workspace:./workspace}")
    private String workspace;

    private final ObjectMapper objectMapper;

    private static final String SOUL_FILE = "soul.json";

    @PostConstruct
    void init() {
        Path path = soulPath();
        if (!Files.exists(path)) {
            saveConfig(defaultConfig());
            log.info("Initialized default soul.json at {}", path);
        }
    }

    /**
     * 获取配置 Map；文件不存在时返回默认值
     */
    public Map<String, Object> getConfig() {
        Path path = soulPath();
        if (!Files.exists(path)) {
            return defaultConfig();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.warn("Failed to read soul.json, returning defaults", e);
            return defaultConfig();
        }
    }

    /**
     * 持久化配置到 soul.json
     */
    public void saveConfig(Map<String, Object> configMap) {
        Path path = soulPath();
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), configMap);
            log.info("Soul config saved: {}", configMap.get("agentName"));
        } catch (IOException e) {
            log.error("Failed to save soul.json", e);
            throw new RuntimeException("Failed to save soul config", e);
        }
    }

    /**
     * 生成系统提示词片段
     */
    public String generateSystemPrompt() {
        Map<String, Object> soul = getConfig();
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

    private Path soulPath() {
        return Path.of(workspace).resolve(SOUL_FILE);
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        return Collections.emptyList();
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("agentName", "JaguarClaw");
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
