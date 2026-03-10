package com.jaguarliu.ai.gateway.rpc.handler.skill;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * skills.get - 获取 skill 详情（包含正文）
 *
 * 请求格式：
 * { "name": "code-review" }
 *
 * 返回格式：
 * {
 *   "name": "code-review",
 *   "description": "代码审查",
 *   "body": "# Code Review\n...",
 *   "allowedTools": ["read_file", "grep"],
 *   "confirmBefore": ["write_file"]
 * }
 */
@Component
@RequiredArgsConstructor
public class SkillGetHandler implements RpcHandler {

    private final SkillRegistry skillRegistry;

    @Override
    public String getMethod() {
        return "skills.get";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();

        if (payload == null) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing payload"));
        }

        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing required field: name"));
        }

        String scope = extractScope(payload);
        String agentId = extractAgentId(payload);

        Optional<SkillEntry> entry = "global".equalsIgnoreCase(scope)
                ? skillRegistry.getByName(name, null)
                : skillRegistry.getByName(name, agentId);
        if (entry.isEmpty()) {
            return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", "Skill not found: " + name));
        }

        Optional<LoadedSkill> skill = "global".equalsIgnoreCase(scope)
                ? skillRegistry.activateGlobal(name)
                : skillRegistry.activate(name, agentId);

        return Mono.just(RpcResponse.success(request.getId(), toDto(entry.get(), skill.orElse(null))));
    }

    private String extractAgentId(Map<String, Object> payload) {
        Object raw = payload.get("agentId");
        if (raw == null || raw.toString().isBlank()) {
            return "main";
        }
        return raw.toString();
    }

    private String extractScope(Map<String, Object> payload) {
        Object raw = payload.get("scope");
        if (raw != null && "global".equalsIgnoreCase(raw.toString())) {
            return "global";
        }
        return "effective";
    }

    private Map<String, Object> toDto(SkillEntry entry, LoadedSkill skill) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", entry.getMetadata().getName());
        dto.put("description", entry.getMetadata().getDescription());
        dto.put("available", entry.isAvailable());
        dto.put("unavailableReason", entry.getUnavailableReason() != null ? entry.getUnavailableReason() : "");
        dto.put("priority", entry.getMetadata().getPriority());
        dto.put("tokenCost", entry.getTokenCost());
        if (skill != null) {
            dto.put("body", skill.getBody());
            dto.put("allowedTools", skill.getAllowedTools() != null ? skill.getAllowedTools() : List.of());
            dto.put("confirmBefore", skill.getConfirmBefore() != null ? skill.getConfirmBefore() : List.of());
        } else {
            dto.put("body", "");
            dto.put("allowedTools", List.of());
            dto.put("confirmBefore", List.of());
        }
        return dto;
    }
}
