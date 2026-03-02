package com.jaguarliu.ai.memory.store;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.tools.ToolsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 全局记忆文件存储
 *
 * 设计原则：
 * - 记忆是全局的、跨会话的（个人助手，非多租户）
 * - Markdown 是真相源（source of truth）
 * - 写入 = 纯文件操作，不触发 embedding
 * - 索引更新由 MemoryIndexer 异步/按需完成
 *
 * 存储结构：
 * workspace/memory/
 *   MEMORY.md           - 核心长期记忆（全局）
 *   2026-01-15.md       - 日记式追加（全局）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryStore {

    private final MemoryProperties memoryProperties;
    private final ToolsProperties toolsProperties;

    private Path workspaceRoot;
    private Path memoryDir;

    private static final String DEFAULT_MEMORY_MD =
            "# Memory\n\n" +
            "## About User\n" +
            "(Not yet known. Update as you learn about the user.)\n\n" +
            "## Memory Files\n" +
            "(Register memory files here as you create them.)\n\n" +
            "---\n\n" +
            "## Usage Principles\n" +
            "- This file is a **lightweight index** — keep it under 50 lines.\n" +
            "- Never write substantive content directly here; create dedicated files instead.\n" +
            "- To save notes: memory_write(content, file=\"projects.md\", scope=\"global\")\n" +
            "- To update this index: memory_write(content, file=\"MEMORY.md\", scope=\"global\")\n" +
            "- Register every new memory file in \"Memory Files\" above.\n" +
            "- Search before writing to avoid duplicates.\n";

    @PostConstruct
    public void init() {
        workspaceRoot = Path.of(toolsProperties.getWorkspace())
                .toAbsolutePath()
                .normalize();

        memoryDir = workspaceRoot
                .resolve(memoryProperties.getPath())
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(memoryDir);
            log.info("Global memory store initialized: {}", memoryDir);

            Path globalMemoryMd = memoryDir.resolve("MEMORY.md");
            if (!Files.exists(globalMemoryMd)) {
                Files.writeString(globalMemoryMd, DEFAULT_MEMORY_MD, StandardCharsets.UTF_8);
                log.info("Initialized default global MEMORY.md: {}", globalMemoryMd);
            }
        } catch (IOException e) {
            log.error("Failed to create memory directory: {}", memoryDir, e);
        }
    }

    /**
     * 获取记忆目录路径
     */
    public Path getMemoryDir() {
        return memoryDir;
    }

    /**
     * 追加内容到核心记忆 MEMORY.md（全局长期记忆）
     */
    public void appendToCore(String content) throws IOException {
        appendToCore(content, null, MemoryScope.GLOBAL);
    }

    public void appendToCore(String content, String agentId, MemoryScope scope) throws IOException {
        Path baseDir = resolveScopeDir(scope, agentId);
        Path corePath = baseDir.resolve("MEMORY.md");
        appendToFile(corePath, content);
        log.info("Appended to {} MEMORY.md: {} chars", scope == MemoryScope.AGENT ? "agent" : "global", content.length());
    }

    /**
     * 追加内容到今天的日记文件（全局日记）
     */
    public void appendToDaily(String content) throws IOException {
        appendToDaily(content, null, MemoryScope.GLOBAL);
    }

    public void appendToDaily(String content, String agentId, MemoryScope scope) throws IOException {
        Path baseDir = resolveScopeDir(scope, agentId);
        String fileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
        Path dailyPath = baseDir.resolve(fileName);
        appendToFile(dailyPath, content);
        log.info("Appended to {} daily log {}: {} chars",
                scope == MemoryScope.AGENT ? "agent" : "global", fileName, content.length());
    }

    /**
     * 追加内容到指定记忆文件（任意命名文件，agent 自行组织）
     */
    public void appendToMemoryFile(String relativePath, String content, String agentId, MemoryScope scope) throws IOException {
        Path baseDir = resolveScopeDir(scope, agentId);
        Path filePath = baseDir.resolve(relativePath).normalize();
        validatePath(filePath, baseDir);
        appendToFile(filePath, content);
        log.info("Appended to {} file {}: {} chars",
                scope == MemoryScope.AGENT ? "agent" : "global", relativePath, content.length());
    }

    /**
     * 追加内容到指定文件
     */
    public void appendToFile(Path filePath, String content) throws IOException {
        validatePath(filePath, inferBaseDir(filePath));

        // 确保父目录存在
        Files.createDirectories(filePath.getParent());

        // 如果文件已存在且不为空，加一个空行分隔
        if (Files.exists(filePath) && Files.size(filePath) > 0) {
            content = "\n" + content;
        }

        Files.writeString(filePath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 读取指定记忆文件
     *
     * @param relativePath 相对于 memory 目录的路径
     * @return 文件内容
     */
    public String read(String relativePath) throws IOException {
        return read(relativePath, null, MemoryScope.GLOBAL);
    }

    public String read(String relativePath, String agentId, MemoryScope scope) throws IOException {
        Path baseDir = resolveScopeDir(scope, agentId);
        Path filePath = baseDir.resolve(relativePath).normalize();
        validatePath(filePath, baseDir);

        if (!Files.exists(filePath)) {
            throw new IOException("Memory file not found: " + relativePath);
        }

        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取指定行范围
     *
     * @param relativePath 相对路径
     * @param startLine    起始行（1-based）
     * @param limit        读取行数
     * @return 指定范围的内容
     */
    public String readLines(String relativePath, int startLine, int limit) throws IOException {
        return readLines(relativePath, startLine, limit, null, MemoryScope.GLOBAL);
    }

    public String readLines(String relativePath, int startLine, int limit, String agentId, MemoryScope scope) throws IOException {
        Path baseDir = resolveScopeDir(scope, agentId);
        Path filePath = baseDir.resolve(relativePath).normalize();
        validatePath(filePath, baseDir);

        if (!Files.exists(filePath)) {
            throw new IOException("Memory file not found: " + relativePath);
        }

        List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        int start = Math.max(0, startLine - 1); // 转为 0-based
        int end = Math.min(allLines.size(), start + limit);

        if (start >= allLines.size()) {
            return "";
        }

        return String.join("\n", allLines.subList(start, end));
    }

    /**
     * 列出所有记忆文件（全局）
     */
    public List<MemoryFileInfo> listFiles() throws IOException {
        return listFiles(MemoryScope.GLOBAL, null);
    }

    public List<MemoryFileInfo> listFiles(MemoryScope scope, String agentId) throws IOException {
        MemoryScope resolvedScope = scope == null ? MemoryScope.GLOBAL : scope;
        if (resolvedScope == MemoryScope.BOTH) {
            List<MemoryFileInfo> merged = new ArrayList<>();
            merged.addAll(listFiles(resolveScopeDir(MemoryScope.GLOBAL, agentId)));
            merged.addAll(listFiles(resolveScopeDir(MemoryScope.AGENT, agentId)));
            return merged.stream()
                    .sorted((a, b) -> b.relativePath().compareTo(a.relativePath()))
                    .toList();
        }
        return listFiles(resolveScopeDir(resolvedScope, agentId));
    }

    /**
     * 检查核心记忆文件是否存在
     */
    public boolean coreMemoryExists() {
        return coreMemoryExists(MemoryScope.GLOBAL, null);
    }

    public boolean coreMemoryExists(MemoryScope scope, String agentId) {
        Path dir = resolveScopeDir(scope, agentId);
        return Files.exists(dir.resolve("MEMORY.md"));
    }

    /**
     * 路径安全校验
     */
    private void validatePath(Path filePath, Path baseDir) throws IOException {
        Path normalized = filePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(baseDir)) {
            throw new IOException("Access denied: path outside memory directory");
        }
    }

    private List<MemoryFileInfo> listFiles(Path baseDir) throws IOException {
        if (!Files.exists(baseDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(baseDir, 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> {
                        try {
                            return new MemoryFileInfo(
                                    baseDir.relativize(p).toString().replace('\\', '/'),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .sorted((a, b) -> b.relativePath().compareTo(a.relativePath()))
                    .toList();
        }
    }

    private Path resolveScopeDir(MemoryScope scope, String agentId) {
        MemoryScope resolvedScope = scope == null ? MemoryScope.GLOBAL : scope;
        return switch (resolvedScope) {
            case GLOBAL -> memoryDir;
            case AGENT -> workspaceRoot
                    .resolve("agents")
                    .resolve(normalizeAgentId(agentId))
                    .resolve(memoryProperties.getPath())
                    .toAbsolutePath()
                    .normalize();
            case BOTH -> throw new IllegalArgumentException("BOTH scope is not supported for single-file operations");
        };
    }

    private Path inferBaseDir(Path filePath) {
        Path normalized = filePath.toAbsolutePath().normalize();
        if (normalized.startsWith(memoryDir)) {
            return memoryDir;
        }
        if (normalized.startsWith(workspaceRoot.resolve("agents").toAbsolutePath().normalize())) {
            int nameCount = normalized.getNameCount();
            int baseCount = workspaceRoot.toAbsolutePath().normalize().getNameCount();
            if (nameCount >= baseCount + 3) {
                return workspaceRoot
                        .resolve("agents")
                        .resolve(normalized.getName(baseCount + 1).toString())
                        .resolve(memoryProperties.getPath())
                        .toAbsolutePath()
                        .normalize();
            }
        }
        return memoryDir;
    }

    private String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "main";
        }
        return agentId;
    }

    /**
     * 记忆文件信息
     */
    public record MemoryFileInfo(String relativePath, long sizeBytes, long lastModifiedMs) {}
}
