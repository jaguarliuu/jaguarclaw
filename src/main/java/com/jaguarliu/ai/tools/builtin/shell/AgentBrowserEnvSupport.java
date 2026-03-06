package com.jaguarliu.ai.tools.builtin.shell;

import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import com.jaguarliu.ai.tools.runtime.BundledRuntimeService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * agent-browser 进程环境变量注入。
 */
public final class AgentBrowserEnvSupport {

    private AgentBrowserEnvSupport() {}

    public static void apply(Map<String, String> env,
                             ToolsProperties properties,
                             BundledRuntimeService bundledRuntimeService) {
        if (env == null || properties == null) {
            return;
        }

        ToolExecutionContext context = ToolExecutionContext.current();
        String agentId = sanitizeSegment(context != null ? context.getAgentId() : null, "main");
        String sessionId = sanitizeSegment(resolveSessionId(context), "default");

        Path profileRoot = resolveProfileRoot(properties);
        Path profilePath = profileRoot.resolve(agentId).resolve(sessionId).toAbsolutePath().normalize();

        try {
            Files.createDirectories(profilePath);
        } catch (Exception ignored) {
            // 目录创建失败不阻断命令执行，交由下游使用默认行为
        }

        env.put("AGENT_BROWSER_AGENT_ID", agentId);
        env.put("AGENT_BROWSER_SESSION", sessionId);
        env.put("AGENT_BROWSER_PROFILE", profilePath.toString());
        env.putIfAbsent("AGENT_BROWSER_PROVIDER", "kernel");
        env.put("AGENT_BROWSER_SKIP_INSTALL", "1");

        if (bundledRuntimeService != null) {
            bundledRuntimeService.resolveBundledBinary("agent-browser")
                    .ifPresent(path -> env.put("AGENT_BROWSER_EXECUTABLE_PATH", path.toString()));
            bundledRuntimeService.resolveBundledChromium()
                    .ifPresent(path -> env.put("AGENT_BROWSER_CHROMIUM_PATH", path.toString()));
            bundledRuntimeService.resolveBundledChromiumHome()
                    .ifPresent(path -> env.put("AGENT_BROWSER_KERNEL_HOME", path.toString()));
        }
    }

    private static String resolveSessionId(ToolExecutionContext context) {
        if (context == null) {
            return "default";
        }
        String sessionId = context.getSessionId();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        String runId = context.getRunId();
        if (runId != null && !runId.isBlank()) {
            return runId;
        }
        return "default";
    }

    private static Path resolveProfileRoot(ToolsProperties properties) {
        Path globalWorkspace = WorkspaceResolver.resolveGlobalWorkspace(properties).toAbsolutePath().normalize();
        Path appDataRoot = globalWorkspace.getParent() != null ? globalWorkspace.getParent() : globalWorkspace;
        return appDataRoot.resolve("browser-profiles").toAbsolutePath().normalize();
    }

    private static String sanitizeSegment(String value, String fallback) {
        String source = (value == null || value.isBlank()) ? fallback : value;
        String normalized = source.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }
}

