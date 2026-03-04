package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.nodeconsole.AuditLogService;
import com.jaguarliu.ai.nodeconsole.NodeEntity;
import com.jaguarliu.ai.nodeconsole.NodeService;
import com.jaguarliu.ai.nodeconsole.RemoteCommandClassifier;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * remote_exec 工具
 * 在远程 SSH 节点上执行命令
 */
@Component
@RequiredArgsConstructor
public class RemoteExecTool implements Tool {

    private final NodeService nodeService;
    private final RemoteCommandClassifier classifier;
    private final AuditLogService auditLogService;
    private final ToolsProperties toolsProperties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("remote_exec")
                .description("在远程 SSH 节点上执行 shell 命令。需要指定节点别名和要执行的命令。只读命令自动执行，有副作用的命令需要用户确认，破坏性命令将被拒绝。使用 node_list 查看可用节点。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "alias", Map.of(
                                        "type", "string",
                                        "description", "目标节点别名"
                                ),
                                "command", Map.of(
                                        "type", "string",
                                        "description", "要执行的 shell 命令"
                                ),
                                "script_path", Map.of(
                                        "type", "string",
                                        "description", "脚本文件路径（相对 workspace 或绝对路径）"
                                ),
                                "script_content", Map.of(
                                        "type", "string",
                                        "description", "内联脚本内容（与 command/script_path 互斥）"
                                ),
                                "interpreter", Map.of(
                                        "type", "string",
                                        "description", "脚本解释器（默认 bash）"
                                ),
                                "args", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "脚本参数列表"
                                )
                        ),
                        "required", List.of("alias")
                ))
                .hitl(false) // 动态由 ToolDispatcher 判断
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String alias = (String) arguments.get("alias");
            String command = asText(arguments.get("command"));
            String scriptPath = asText(arguments.get("script_path"));
            String scriptContent = asText(arguments.get("script_content"));
            String interpreter = asText(arguments.get("interpreter"));
            List<String> scriptArgs = toStringList(arguments.get("args"));

            if (alias == null || alias.isBlank()) {
                return ToolResult.error("alias is required");
            }

            int modeCount = 0;
            if (command != null && !command.isBlank()) modeCount++;
            if (scriptPath != null && !scriptPath.isBlank()) modeCount++;
            if (scriptContent != null && !scriptContent.isBlank()) modeCount++;
            if (modeCount == 0) {
                return ToolResult.error("One of command/script_path/script_content is required");
            }
            if (modeCount > 1) {
                return ToolResult.error("command, script_path and script_content are mutually exclusive");
            }

            CommandPayload payload;
            try {
                payload = resolveCommandPayload(command, scriptPath, scriptContent, interpreter, scriptArgs);
            } catch (Exception e) {
                return ToolResult.error("Invalid script input: " + e.getMessage());
            }

            // 获取节点信息
            String nodeId = null;
            String connectorType = null;
            var nodeOpt = nodeService.findByAlias(alias);
            if (nodeOpt.isPresent()) {
                NodeEntity node = nodeOpt.get();
                nodeId = node.getId();
                connectorType = node.getConnectorType();
            }

            // 安全检查：分类命令
            String policy = nodeService.getSafetyPolicy(alias);
            var classification = classifier.classify(payload.classificationInput(), policy);
            String safetyLevel = classification.safetyLevel().name().toLowerCase();

            // 检查是否被阻止（破坏性命令）
            if (classification.isBlocked()) {
                auditLogService.logCommandExecution(
                        "command.reject", alias, nodeId, connectorType,
                        "remote_exec", payload.auditCommand(), safetyLevel, policy,
                        false, null,
                        "blocked", classification.reason(), 0);
                return ToolResult.error("Command blocked: " + classification.reason());
            }

            // 检查是否需要 HITL（此时应该已经通过 HITL 确认了）
            boolean hitlRequired = classification.requiresHitl();

            long startTime = System.currentTimeMillis();
            try {
                String output = nodeService.executeCommandAfterHitl(alias, payload.executionCommand());
                long durationMs = System.currentTimeMillis() - startTime;

                auditLogService.logCommandExecution(
                        "command.execute", alias, nodeId, connectorType,
                        "remote_exec", payload.auditCommand(), safetyLevel, policy,
                        hitlRequired, hitlRequired ? "approve" : null,
                        "success", output, durationMs);

                return ToolResult.success(output);
            } catch (Exception e) {
                long durationMs = System.currentTimeMillis() - startTime;

                auditLogService.logCommandExecution(
                        "command.execute", alias, nodeId, connectorType,
                        "remote_exec", payload.auditCommand(), safetyLevel, policy,
                        hitlRequired, hitlRequired ? "approve" : null,
                        "error", e.getMessage(), durationMs);

                return ToolResult.error(e.getMessage());
            }
        });
    }

    private CommandPayload resolveCommandPayload(
            String command,
            String scriptPath,
            String scriptContent,
            String interpreter,
            List<String> args
    ) throws Exception {
        if (command != null && !command.isBlank()) {
            String normalized = command.trim();
            return new CommandPayload(normalized, normalized, summarizeAudit(normalized));
        }

        String script = scriptContent;
        if ((script == null || script.isBlank()) && scriptPath != null && !scriptPath.isBlank()) {
            Path path = resolveScriptPath(scriptPath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("script_path does not exist or is not a file: " + scriptPath);
            }
            long size = Files.size(path);
            if (size > toolsProperties.getMaxFileSize()) {
                throw new IllegalArgumentException("script file too large: " + size + " bytes");
            }
            script = Files.readString(path, StandardCharsets.UTF_8);
        }

        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("script content is empty");
        }

        String interpreterToUse = (interpreter == null || interpreter.isBlank()) ? "bash" : interpreter.trim();
        String executionCommand = buildRemoteScriptCommand(script, interpreterToUse, args);
        return new CommandPayload(executionCommand, script, summarizeAudit("script<" + interpreterToUse + ">"));
    }

    private Path resolveScriptPath(String pathStr) {
        Path globalWorkspace = WorkspaceResolver.resolveGlobalWorkspace(toolsProperties);
        ToolExecutionContext ctx = ToolExecutionContext.current();
        boolean isRelative = !Path.of(pathStr).isAbsolute();

        if (isRelative && ctx != null && ctx.getAgentId() != null) {
            Path agentWorkspace = globalWorkspace.resolve("workspace-" + ctx.getAgentId()).normalize();
            Path candidate = agentWorkspace.resolve(pathStr).normalize();
            if (candidate.startsWith(agentWorkspace)) {
                return candidate;
            }
        }

        if (isRelative && ctx != null) {
            for (Path allowedPath : ctx.getAdditionalAllowedPaths()) {
                Path candidate = allowedPath.resolve(pathStr).normalize();
                if (candidate.startsWith(allowedPath)) {
                    return candidate;
                }
            }
        }

        if (isRelative) {
            Path candidate = globalWorkspace.resolve(pathStr).normalize();
            if (candidate.startsWith(globalWorkspace)) {
                return candidate;
            }
        } else {
            Path absolute = Path.of(pathStr).toAbsolutePath().normalize();
            if (absolute.startsWith(globalWorkspace)) {
                return absolute;
            }
            if (ctx != null && ctx.isPathAllowed(absolute)) {
                return absolute;
            }
        }

        throw new IllegalArgumentException("script_path is outside allowed workspace");
    }

    private String buildRemoteScriptCommand(String scriptContent, String interpreter, List<String> args) {
        String encoded = Base64.getEncoder().encodeToString(scriptContent.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        sb.append("printf %s ").append(quoteSh(encoded))
                .append(" | base64 -d | ")
                .append(quoteSh(interpreter))
                .append(" -s");
        if (args != null && !args.isEmpty()) {
            for (String arg : args) {
                sb.append(" ").append(quoteSh(arg));
            }
        }
        return sb.toString();
    }

    private static String quoteSh(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String summarizeAudit(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.length() <= 180) {
            return trimmed;
        }
        return trimmed.substring(0, 180) + "...";
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private record CommandPayload(String executionCommand, String classificationInput, String auditCommand) {}
}
