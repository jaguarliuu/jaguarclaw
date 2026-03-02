package com.jaguarliu.ai.heartbeat;

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
 * Heartbeat 配置服务（Agent 作用域）
 * 文件存储在：{workspace}/agents/{agentId}/heartbeat.json 与 HEARTBEAT.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatConfigService {

    private final ObjectMapper objectMapper;
    private final AgentWorkspaceResolver workspaceResolver;

    private static final String HEARTBEAT_JSON = "heartbeat.json";
    private static final String HEARTBEAT_MD = "HEARTBEAT.md";

    public Map<String, Object> getConfig() {
        return getConfig("main");
    }

    public Map<String, Object> getConfig(String agentId) {
        Path path = configPath(agentId);
        if (!Files.exists(path)) {
            return defaultConfig();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.warn("Failed to read heartbeat.json, returning defaults", e);
            return defaultConfig();
        }
    }

    public void saveConfig(Map<String, Object> configMap) {
        saveConfig("main", configMap);
    }

    public void saveConfig(String agentId, Map<String, Object> configMap) {
        Path path = configPath(agentId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), configMap);
            log.info("Heartbeat config saved");
        } catch (IOException e) {
            log.error("Failed to save heartbeat.json", e);
            throw new RuntimeException("Failed to save heartbeat config", e);
        }
    }

    public String readHeartbeatMd() {
        return readHeartbeatMd("main");
    }

    public String readHeartbeatMd(String agentId) {
        Path path = mdPath(agentId);
        if (!Files.exists(path)) {
            return defaultHeartbeatMd();
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            log.warn("Failed to read HEARTBEAT.md", e);
            return defaultHeartbeatMd();
        }
    }

    public void writeHeartbeatMd(String content) {
        writeHeartbeatMd("main", content);
    }

    public void writeHeartbeatMd(String agentId, String content) {
        Path path = mdPath(agentId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            log.info("HEARTBEAT.md updated");
        } catch (IOException e) {
            log.error("Failed to write HEARTBEAT.md", e);
            throw new RuntimeException("Failed to write HEARTBEAT.md", e);
        }
    }

    public void ensureAgentDefaults(String agentId) {
        Path configPath = configPath(agentId);
        if (!Files.exists(configPath)) {
            saveConfig(agentId, defaultConfig());
            log.info("Initialized default heartbeat.json at {}", configPath);
        }

        Path mdPath = mdPath(agentId);
        if (!Files.exists(mdPath)) {
            try {
                Files.createDirectories(mdPath.getParent());
                Files.writeString(mdPath, defaultHeartbeatMd());
                log.info("Initialized default HEARTBEAT.md at {}", mdPath);
            } catch (IOException e) {
                log.error("Failed to create HEARTBEAT.md", e);
            }
        }
    }

    private Path configPath(String agentId) {
        return workspaceResolver.resolveAgentFile(agentId, HEARTBEAT_JSON);
    }

    private Path mdPath(String agentId) {
        return workspaceResolver.resolveAgentFile(agentId, HEARTBEAT_MD);
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("intervalMinutes", 30);
        config.put("activeHoursStart", "09:00");
        config.put("activeHoursEnd", "22:00");
        config.put("timezone", "Asia/Shanghai");
        config.put("ackMaxChars", 300);
        return config;
    }

    private String defaultHeartbeatMd() {
        return """
                # Heartbeat Checklist

                每次心跳时检查以下内容。如果没有需要汇报的内容，只回复：HEARTBEAT_OK
                否则用简短自然语言描述需要告知用户的内容。

                ## 检查清单
                - 有没有今天需要提醒用户的事项（来自记忆）？
                - 有没有未完成的重要任务需要跟进？
                - 今天是否还没有主动问候过（每天最多一次）？

                ## 规则
                - 默认静默，只在有实际价值时才打扰用户
                - 同一天内主动问候不超过一次
                """;
    }
}
