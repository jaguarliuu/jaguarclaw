package com.jaguarliu.ai.gateway.security;

import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * RPC 方法授权服务（method -> permission）
 */
@Service
public class RpcAuthorizationService {

    private static final Set<String> PUBLIC_METHODS = Set.of(
            "ping",
            "auth.local.bootstrap",
            "auth.refresh"
    );

    private static final Set<String> READ_METHODS = Set.of(
            "app.status",
            "session.get",
            "session.list",
            "session.files.list",
            "message.list",
            "run.get",
            "tool.list",
            "skills.get",
            "skills.list",
            "soul.get",
            "memory.status",
            "subagent.list",
            "schedule.list",
            "schedule.runs.list",
            "schedule.runs.get",
            "nodes.list"
    );

    private static final Set<String> WRITE_METHODS = Set.of(
            "agent.run",
            "agent.cancel",
            "session.create",
            "session.delete",
            "subagent.send",
            "subagent.stop",
            "soul.save",
            "schedule.create",
            "schedule.update",
            "schedule.delete",
            "schedule.toggle",
            "schedule.run",
            "skills.upload",
            "tool.confirm"
    );

    private static final Set<String> ADMIN_METHODS = Set.of(
            "audit.logs.list"
    );

    private static final Map<String, EnumSet<RpcPermission>> ROLE_PERMISSIONS = Map.of(
            "local_admin", EnumSet.of(
                    RpcPermission.PUBLIC,
                    RpcPermission.READ,
                    RpcPermission.WRITE,
                    RpcPermission.CONFIG,
                    RpcPermission.DANGEROUS,
                    RpcPermission.ADMIN
            ),
            "local_limited", EnumSet.of(
                    RpcPermission.PUBLIC,
                    RpcPermission.READ,
                    RpcPermission.WRITE
            )
    );

    public boolean isAuthorized(ConnectionPrincipal principal, String method) {
        RpcPermission requiredPermission = resolveRequiredPermission(method);
        if (requiredPermission == RpcPermission.PUBLIC) {
            return true;
        }
        if (principal == null || principal.getRoles() == null || principal.getRoles().isEmpty()) {
            return false;
        }

        for (String role : principal.getRoles()) {
            Set<RpcPermission> permissions = ROLE_PERMISSIONS.get(role);
            if (permissions != null && permissions.contains(requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    public RpcPermission resolveRequiredPermission(String method) {
        if (PUBLIC_METHODS.contains(method)) {
            return RpcPermission.PUBLIC;
        }
        if (method.equals("tool.execute")) {
            return RpcPermission.DANGEROUS;
        }
        if (isConfigMethod(method)) {
            return RpcPermission.CONFIG;
        }
        if (ADMIN_METHODS.contains(method)) {
            return RpcPermission.ADMIN;
        }
        if (READ_METHODS.contains(method)) {
            return RpcPermission.READ;
        }
        if (WRITE_METHODS.contains(method)) {
            return RpcPermission.WRITE;
        }

        // 未分类方法默认按 WRITE 处理（保守策略）
        return RpcPermission.WRITE;
    }

    private boolean isConfigMethod(String method) {
        return method.startsWith("llm.config.")
                || method.startsWith("mcp.servers.")
                || method.startsWith("tools.config.")
                || method.startsWith("nodes.")
                || method.equals("memory.rebuild");
    }
}
