package com.jaguarliu.ai.tools.builtin.shell;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Shell 命令执行工具
 * 默认不需要 HITL 确认，但危险命令会触发确认
 * 支持 Windows 和 Linux/Mac
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShellTool implements Tool {

    private final ToolsProperties properties;

    /**
     * 命令执行超时（秒）
     */
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * 最大输出长度
     */
    private static final int MAX_OUTPUT_LENGTH = 32000;

    /**
     * 是否为 Windows 系统
     */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    /**
     * 用于异步执行的线程池
     */
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public ToolDefinition getDefinition() {
        String osHint = IS_WINDOWS ? "当前为 Windows 环境" : "当前为 Linux/Mac 环境";
        return ToolDefinition.builder()
                .name("shell")
                .description("执行 shell 命令。" + osHint)
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "要执行的命令"),
                                "script_path", Map.of("type", "string", "description", "脚本文件路径（相对 workspace 或绝对路径）"),
                                "script_content", Map.of("type", "string", "description", "内联脚本内容（与 command/script_path 互斥）"),
                                "interpreter", Map.of("type", "string", "description", "脚本解释器（默认 bash/powershell）"),
                                "args", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "description", "脚本参数列表"
                                )
                        ),
                        "required", List.of()
                ))
                .hitl(false)
                .tags(List.of("shell", "exec", "system"))
                .riskLevel("high")
                .parameterSummary("command | script_path | script_content (one required)")
                .example("shell({ script_path: 'scripts/inspect.sh', args: ['--fast'] })")
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String command = asText(arguments.get("command"));
            String scriptPath = asText(arguments.get("script_path"));
            String scriptContent = asText(arguments.get("script_content"));
            String interpreter = asText(arguments.get("interpreter"));
            List<String> scriptArgs = toStringList(arguments.get("args"));

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

            Process process = null;
            Path tempScriptPath = null;
            try {
                // 工作目录：skill 激活时使用 skill 资源目录，否则使用 session workspace
                Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
                Path workingDir = resolveWorkingDirectory(workspacePath);

                String effectiveCommand;
                if (command != null && !command.isBlank()) {
                    effectiveCommand = command;
                } else {
                    Path executableScriptPath;
                    if (scriptPath != null && !scriptPath.isBlank()) {
                        executableScriptPath = resolveScriptPath(scriptPath, workspacePath);
                        if (!Files.exists(executableScriptPath) || !Files.isRegularFile(executableScriptPath)) {
                            return ToolResult.error("script_path does not exist or is not a file: " + scriptPath);
                        }
                    } else {
                        executableScriptPath = writeTempScript(scriptContent, workspacePath, interpreter);
                        tempScriptPath = executableScriptPath;
                    }
                    effectiveCommand = buildScriptCommand(executableScriptPath, interpreter, scriptArgs);
                }

                log.info("Executing shell command: {}", summarizeCommandForLog(effectiveCommand));

                // 构建进程
                ProcessBuilder pb = buildProcess(effectiveCommand);
                pb.directory(workingDir.toFile());

                // 将 workspace 路径注入环境变量，供脚本输出文件使用
                pb.environment().put("WORKSPACE_DIR", workspacePath.toString());

                // 合并 stdout 和 stderr
                pb.redirectErrorStream(true);

                process = pb.start();
                final Process finalProcess = process;

                // 异步读取输出
                Future<String> outputFuture = executor.submit(() -> readOutput(finalProcess));

                // 等待结果，带超时
                String output;
                try {
                    output = outputFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // 超时，强制终止进程
                    finalProcess.destroyForcibly();
                    outputFuture.cancel(true);
                    log.warn("Shell command timed out: {}", summarizeCommandForLog(effectiveCommand));
                    return ToolResult.error("Command timed out after " + TIMEOUT_SECONDS + " seconds. The process has been terminated.");
                }

                int exitCode = finalProcess.waitFor();

                log.info("Shell command completed: exitCode={}, outputLength={}",
                        exitCode, output.length());

                if (exitCode == 0) {
                    return ToolResult.success(output.isEmpty() ? "(no output)" : output);
                } else {
                    return ToolResult.error("Exit code: " + exitCode + "\n" + output);
                }

            } catch (Exception e) {
                log.error("Shell command failed", e);
                if (process != null) {
                    process.destroyForcibly();
                }
                return ToolResult.error("Command execution failed: " + e.getMessage());
            } finally {
                if (tempScriptPath != null) {
                    try {
                        Files.deleteIfExists(tempScriptPath);
                    } catch (Exception ignore) {
                        log.debug("Failed to delete temp script: {}", tempScriptPath);
                    }
                }
            }
        });
    }

    /**
     * 读取进程输出
     */
    private String readOutput(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        Charset charset = IS_WINDOWS ? Charset.forName("GBK") : Charset.defaultCharset();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(line);

                // 输出过长时截断
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output.append("\n\n[Truncated: output exceeds ").append(MAX_OUTPUT_LENGTH).append(" chars]");
                    break;
                }
            }
        }

        return output.toString();
    }

    /**
     * 确定命令的工作目录
     * skill 激活时使用 skill 资源目录（脚本和引用文件在那里），
     * 否则使用 session workspace。
     */
    private Path resolveWorkingDirectory(Path sessionWorkspace) {
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) {
            Set<Path> allowedPaths = ctx.getAdditionalAllowedPaths();
            if (!allowedPaths.isEmpty()) {
                // skill 激活时，使用 skill 资源目录作为工作目录
                Path skillBasePath = allowedPaths.iterator().next();
                if (Files.isDirectory(skillBasePath)) {
                    log.debug("Using skill base path as working directory: {}", skillBasePath);
                    return skillBasePath;
                }
            }
        }

        // 确保 session workspace 目录存在
        try {
            Files.createDirectories(sessionWorkspace);
        } catch (Exception e) {
            log.warn("Failed to create session workspace: {}", sessionWorkspace, e);
        }
        return sessionWorkspace;
    }

    /**
     * 根据操作系统构建进程
     */
    private ProcessBuilder buildProcess(String command) {
        if (IS_WINDOWS) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("/bin/sh", "-c", command);
        }
    }

    private Path resolveScriptPath(String pathStr, Path sessionWorkspace) {
        Path globalWorkspace = WorkspaceResolver.resolveGlobalWorkspace(properties);
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
            Path workspaceCandidate = sessionWorkspace.resolve(pathStr).normalize();
            if (workspaceCandidate.startsWith(sessionWorkspace)) {
                return workspaceCandidate;
            }
            Path globalCandidate = globalWorkspace.resolve(pathStr).normalize();
            if (globalCandidate.startsWith(globalWorkspace)) {
                return globalCandidate;
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

    private Path writeTempScript(String content, Path workspacePath, String interpreter) throws Exception {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("script_content is empty");
        }

        Path tempDir = workspacePath.resolve(".tmp").normalize();
        Files.createDirectories(tempDir);

        String ext;
        if (IS_WINDOWS) {
            String normalized = interpreter != null ? interpreter.trim().toLowerCase() : "";
            ext = normalized.startsWith("cmd") ? ".cmd" : ".ps1";
        } else {
            ext = ".sh";
        }

        Path tempScript = tempDir.resolve("jaguarclaw-script-" + UUID.randomUUID() + ext).normalize();
        if (!tempScript.startsWith(tempDir)) {
            throw new IllegalArgumentException("failed to create temp script in workspace");
        }
        Files.writeString(tempScript, content, StandardCharsets.UTF_8);

        if (!IS_WINDOWS) {
            try {
                Files.setPosixFilePermissions(tempScript, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                ));
            } catch (UnsupportedOperationException ignored) {
                // 非 POSIX 文件系统忽略
            }
        }
        return tempScript;
    }

    private String buildScriptCommand(Path scriptPath, String interpreter, List<String> args) {
        if (IS_WINDOWS) {
            String shell = (interpreter == null || interpreter.isBlank())
                    ? "powershell.exe"
                    : interpreter.trim();
            StringBuilder cmd = new StringBuilder();
            if (shell.toLowerCase().startsWith("powershell")) {
                cmd.append("powershell.exe -NoProfile -ExecutionPolicy Bypass -File ")
                        .append(quoteWindows(scriptPath.toString()));
            } else if (shell.equalsIgnoreCase("cmd") || shell.equalsIgnoreCase("cmd.exe")) {
                cmd.append("cmd.exe /c ").append(quoteWindows(scriptPath.toString()));
            } else {
                cmd.append(quoteWindows(shell)).append(" ").append(quoteWindows(scriptPath.toString()));
            }
            for (String arg : args) {
                cmd.append(" ").append(quoteWindows(arg));
            }
            return cmd.toString();
        }

        String shell = (interpreter == null || interpreter.isBlank()) ? "/bin/bash" : interpreter.trim();
        StringBuilder cmd = new StringBuilder();
        cmd.append(quoteSh(shell)).append(" ").append(quoteSh(scriptPath.toString()));
        for (String arg : args) {
            cmd.append(" ").append(quoteSh(arg));
        }
        return cmd.toString();
    }

    private static String quoteWindows(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String quoteSh(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String summarizeCommandForLog(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.length() <= 200) {
            return trimmed;
        }
        return trimmed.substring(0, 200) + "...";
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
}
