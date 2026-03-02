package com.jaguarliu.ai.soul;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Soul 配置服务（Agent 作用域）
 * 文件存储在：{workspace}/agents/{agentId}/SOUL.md, RULE.md, PROFILE.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulConfigService {

    private final AgentWorkspaceResolver workspaceResolver;

    private static final String SOUL_MD_FILE = "SOUL.md";
    private static final String RULE_MD_FILE = "RULE.md";
    private static final String PROFILE_MD_FILE = "PROFILE.md";

    // ── SOUL.md ──────────────────────────────────────────────────────────────

    public String readSoulMd(String agentId) {
        return readMd(agentId, SOUL_MD_FILE);
    }

    public void writeSoulMd(String agentId, String content) {
        writeMd(agentId, SOUL_MD_FILE, content);
    }

    // ── RULE.md ──────────────────────────────────────────────────────────────

    public String readRuleMd(String agentId) {
        return readMd(agentId, RULE_MD_FILE);
    }

    public void writeRuleMd(String agentId, String content) {
        writeMd(agentId, RULE_MD_FILE, content);
    }

    // ── PROFILE.md ───────────────────────────────────────────────────────────

    public String readProfileMd(String agentId) {
        return readMd(agentId, PROFILE_MD_FILE);
    }

    public void writeProfileMd(String agentId, String content) {
        writeMd(agentId, PROFILE_MD_FILE, content);
    }

    // ── Bootstrap ────────────────────────────────────────────────────────────

    public void ensureAgentDefaults(String agentId) {
        ensureAgentDefaults(agentId, null);
    }

    /**
     * 确保 3 个 MD 文件已初始化，displayName 用于 SOUL.md 默认模板中的 agentName。
     */
    public void ensureAgentDefaults(String agentId, String displayName) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        String agentName = (displayName != null && !displayName.isBlank()) ? displayName : resolvedAgentId;

        Path soulPath = agentFilePath(resolvedAgentId, SOUL_MD_FILE);
        if (!Files.exists(soulPath)) {
            writeMd(resolvedAgentId, SOUL_MD_FILE, defaultSoulMd(agentName));
            log.info("Initialized default SOUL.md for agentId={}", resolvedAgentId);
        }

        Path rulePath = agentFilePath(resolvedAgentId, RULE_MD_FILE);
        if (!Files.exists(rulePath)) {
            writeMd(resolvedAgentId, RULE_MD_FILE, DEFAULT_RULE_MD);
            log.info("Initialized default RULE.md for agentId={}", resolvedAgentId);
        }

        Path profilePath = agentFilePath(resolvedAgentId, PROFILE_MD_FILE);
        if (!Files.exists(profilePath)) {
            writeMd(resolvedAgentId, PROFILE_MD_FILE, DEFAULT_PROFILE_MD);
            log.info("Initialized default PROFILE.md for agentId={}", resolvedAgentId);
        }

        // 初始化 agent 私有 memory/MEMORY.md（若不存在）
        Path agentWorkspace = workspaceResolver.resolveAgentWorkspace(resolvedAgentId);
        Path agentMemoryMd = agentWorkspace.resolve("memory").resolve("MEMORY.md")
                .toAbsolutePath().normalize();
        if (!Files.exists(agentMemoryMd)) {
            try {
                Files.createDirectories(agentMemoryMd.getParent());
                Files.writeString(agentMemoryMd, defaultAgentMemoryMd(agentName), StandardCharsets.UTF_8);
                log.info("Initialized default memory/MEMORY.md for agentId={}", resolvedAgentId);
            } catch (IOException e) {
                log.warn("Failed to initialize memory/MEMORY.md for agentId={}", resolvedAgentId, e);
            }
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private String readMd(String agentId, String filename) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Path path = agentFilePath(resolvedAgentId, filename);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            log.warn("Failed to read {} for agentId={}", filename, resolvedAgentId, e);
            return "";
        }
    }

    private void writeMd(String agentId, String filename, String content) {
        String resolvedAgentId = workspaceResolver.normalizeAgentId(agentId);
        Path path = agentFilePath(resolvedAgentId, filename);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content != null ? content : "");
            log.info("Wrote {} for agentId={}", filename, resolvedAgentId);
        } catch (IOException e) {
            log.error("Failed to write {} for agentId={}", filename, resolvedAgentId, e);
            throw new RuntimeException("Failed to write " + filename, e);
        }
    }

    private Path agentFilePath(String agentId, String filename) {
        return workspaceResolver.resolveAgentFile(agentId, filename);
    }

    // ── Default templates ─────────────────────────────────────────────────────

    private static String defaultSoulMd(String agentName) {
        return "# Soul\n\n" +
               "Your name is " + agentName + ".\n\n" +
               "## Personality\n" +
               "A helpful and professional AI assistant.\n\n" +
               "## Response Style\n" +
               "- Tone: balanced\n" +
               "- Detail level: balanced\n";
    }

    private static String defaultAgentMemoryMd(String agentName) {
        return "# Memory — " + agentName + "\n\n" +
               "## Memory Files\n" +
               "(Register memory files here as you create them.)\n\n" +
               "---\n\n" +
               "## Usage Principles\n" +
               "- This file is a lightweight index — keep it under 50 lines.\n" +
               "- Never write substantive content directly here; create dedicated files instead.\n" +
               "- To save notes: memory_write(content, file=\"notes.md\", scope=\"agent\")\n" +
               "- To update this index: memory_write(content, file=\"MEMORY.md\", scope=\"agent\")\n";
    }

    private static final String DEFAULT_RULE_MD =
            "# Rules\n\n" +
            "## Behavioral Constraints\n" +
            "- Be honest and transparent with the user.\n" +
            "- Do not perform irreversible actions without explicit confirmation.\n" +
            "- Respect user privacy; avoid storing sensitive data unless asked.\n" +
            "- Keep responses concise unless more detail is requested.\n" +
            "- Memory index discipline: MEMORY.md is a lightweight index only (< 50 lines).\n" +
            "  Never write substantive content directly into MEMORY.md.\n" +
            "  Write to a dedicated file (e.g. file=\"projects.md\"), then update the index in MEMORY.md.\n";

    private static final String DEFAULT_PROFILE_MD =
            "# User Profile\n\n" +
            "## Preferences\n" +
            "- How to address the user: not specified (use a friendly default)\n\n" +
            "## Notes\n" +
            "(Fill in as you learn about the user's preferences and habits.)\n";
}
