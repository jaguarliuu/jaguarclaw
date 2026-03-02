package com.jaguarliu.ai.gateway.rpc.handler.agent;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent DTO 映射及 payload 解析工具
 */
final class AgentDtoMapper {

    private AgentDtoMapper() {}

    static Map<String, Object> toDto(AgentProfileEntity entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entity.getId());
        dto.put("name", entity.getName());
        dto.put("displayName", entity.getDisplayName());
        dto.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        dto.put("workspacePath", entity.getWorkspacePath());
        dto.put("model", entity.getModel() != null ? entity.getModel() : "");
        dto.put("enabled", entity.getEnabled());
        dto.put("isDefault", entity.getIsDefault());
        dto.put("allowedTools", entity.getAllowedTools() != null ? entity.getAllowedTools() : "[]");
        dto.put("excludedTools", entity.getExcludedTools() != null ? entity.getExcludedTools() : "[]");
        dto.put("heartbeatInterval", entity.getHeartbeatInterval() != null ? entity.getHeartbeatInterval() : 0);
        dto.put("heartbeatActiveHours", entity.getHeartbeatActiveHours() != null ? entity.getHeartbeatActiveHours() : "");
        dto.put("dailyTokenLimit", entity.getDailyTokenLimit() != null ? entity.getDailyTokenLimit() : 0);
        dto.put("monthlyCostLimit", entity.getMonthlyCostLimit() != null ? entity.getMonthlyCostLimit() : 0.0);
        dto.put("createdAt", entity.getCreatedAt().toString());
        dto.put("updatedAt", entity.getUpdatedAt().toString());
        return dto;
    }

    static String asString(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    static Boolean asBoolean(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    static Integer asInteger(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    static Double asDouble(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(value.toString());
    }

    static List<String> asStringList(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return null;
    }

    static String extractString(Object payload, String key) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }
}
