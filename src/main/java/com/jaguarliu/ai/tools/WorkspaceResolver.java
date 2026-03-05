package com.jaguarliu.ai.tools;

import java.nio.file.Path;

/**
 * Session workspace 解析工具类
 * 集中 session workspace 路径解析逻辑，供所有工具复用。
 */
public final class WorkspaceResolver {

    private WorkspaceResolver() {
    }

    /**
     * 解析当前 agent 的 workspace 路径。
     * 优先读取 ToolExecutionContext 中预解析的 sessionWorkspacePath；
     * 无上下文时返回全局 workspace/。
     */
    public static Path resolveSessionWorkspace(ToolsProperties properties) {
        Path base = resolveGlobalWorkspace(properties);
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx == null) {
            return base;
        }
        if (ctx.getSessionWorkspacePath() == null) {
            throw new IllegalStateException("Missing sessionWorkspacePath in ToolExecutionContext");
        }
        return ctx.getSessionWorkspacePath();
    }

    /**
     * 获取全局 workspace（用于 fallback 场景如 uploads）
     */
    public static Path resolveGlobalWorkspace(ToolsProperties properties) {
        return Path.of(properties.getWorkspace()).toAbsolutePath().normalize();
    }
}
