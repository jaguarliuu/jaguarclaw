package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.heartbeat.HeartbeatConfigService;
import com.jaguarliu.ai.mcp.prompt.McpPromptProvider;
import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.runtime.prompt.KernelPromptFacet;
import com.jaguarliu.ai.runtime.prompt.MemoryPromptFacet;
import com.jaguarliu.ai.runtime.prompt.PromptAssemblyContext;
import com.jaguarliu.ai.runtime.prompt.SoulPromptFacet;
import com.jaguarliu.ai.runtime.prompt.RulePromptFacet;
import com.jaguarliu.ai.runtime.prompt.ProfilePromptFacet;
import com.jaguarliu.ai.runtime.prompt.ToolPromptFacet;
import com.jaguarliu.ai.skills.index.SkillIndexBuilder;
import com.jaguarliu.ai.soul.SoulConfigService;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * System Prompt 构建器
 *
 * 参考 OpenClaw 的结构化设计，构建包含以下固定段落的系统提示：
 * 1. Identity - 基本身份
 * 2. Tooling - 工具列表和说明
 * 3. Safety - 安全防护提醒
 * 4. Memory - 全局记忆系统使用说明
 * 5. Skills - 技能使用方式（当有可用技能时）
 * 6. Workspace - 工作目录
 * 7. Current Date & Time - 当前时间
 * 8. Runtime - 运行环境信息
 * 9. MCP Server Capabilities - MCP 服务器提供的提示词（如果有）
 *
 * 支持三种 Prompt Mode：
 * - FULL: 完整提示（默认）
 * - MINIMAL: 精简提示（用于子代理）
 * - NONE: 仅身份行
 */
@Slf4j
@Component
public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;
    private final SkillIndexBuilder skillIndexBuilder;
    private final MemorySearchService memorySearchService;
    private final Optional<McpPromptProvider> mcpPromptProvider;
    private final SoulConfigService soulConfigService;
    private final Optional<HeartbeatConfigService> heartbeatConfigService;
    private final Optional<MemoryStore> memoryStore;

    @Value("${tools.workspace:./workspace}")
    private String workspace;

    @Value("${agent.custom-system-prompt:}")
    private String customSystemPrompt;

    @Value("${tools.runtime.enabled:false}")
    private boolean bundledRuntimeEnabled;

    @Value("${tools.runtime.home:}")
    private String bundledRuntimeHome;

    private final KernelPromptFacet kernelPromptFacet;
    private final SoulPromptFacet soulPromptFacet;
    private final RulePromptFacet rulePromptFacet;
    private final ProfilePromptFacet profilePromptFacet;
    private final ToolPromptFacet toolPromptFacet;
    private final MemoryPromptFacet memoryPromptFacet;

    // 身份段落（通用，不含名字 — 名字由 SOUL.md 管理）
    private static final String IDENTITY_SECTION = """
        You are an AI coding assistant. You help users with software engineering tasks including:
        - Writing, reviewing, and debugging code
        - Explaining technical concepts
        - File operations and shell commands
        - Creating documents (PPTX, XLSX, etc.)

        Respond concisely and accurately. Use Chinese when the user writes in Chinese.
        """;

    // 无名字时的首次对话提醒
    private static final String NAME_REMINDER_INSTRUCTION = """

        **First Conversation — Introduce Yourself**

        You don't have a name yet. At the very start of your FIRST response, \
        warmly introduce yourself to the user. Acknowledge that you're new and \
        unnamed, express that you'd love to have a name, and invite the user to \
        give you one. Keep it light and natural — don't make it feel like a \
        system alert. Adapt the language to the user (Chinese if they write in \
        Chinese). After they provide a name, use the soul tools to save it. \
        Only do this introduction once.
        """;

    // 安全段落
    private static final String SAFETY_SECTION = """
        ## Safety Guidelines

        - Do not execute destructive operations without explicit user confirmation
        - Avoid accessing sensitive files (credentials, private keys, etc.) unless necessary
        - When uncertain, ask for clarification before proceeding
        - Do not bypass security measures or perform unauthorized actions
        - Respect file system boundaries (stay within workspace when possible)
        """;

    // Worker 执行策略段落（兼容 sessions_spawn）
    private static final String SUBAGENT_SECTION = """
        ## Worker Task Execution (sessions_spawn)

        You have the ability to spawn worker tasks — independent executions that run asynchronously \
        in isolated sessions. Use the `sessions_spawn` tool to delegate work.

        This is a **worker execution mechanism**, not the multi-agent architecture layer.
        Multi-agent means peer agents selected by `agentId`; `sessions_spawn` only handles async delegation.

        **When to use worker tasks:**
        - **Long-running tasks**: Tasks that may take significant time (network requests, large file processing, \
        complex computations). Spawn a worker so the user can continue chatting.
        - **Parallel independent tasks**: Multiple tasks with no dependencies between them. Spawn one worker \
        per task to run them concurrently.
        - **Isolated context tasks**: Tasks that benefit from a clean, focused context (e.g., researching a \
        specific topic, generating a standalone artifact).

        **Examples of good worker usage:**
        - "Monitor this URL every 30 seconds for 5 minutes" → spawn a worker for monitoring
        - "Analyze these 3 log files for errors" → spawn 3 workers, one per file
        - "Research best practices for X while I work on Y" → spawn a worker for research
        - "Run these tests and report back" → spawn a worker for test execution

        **When NOT to use worker tasks:**
        - Simple, fast operations (reading a file, quick calculations)
        - Tasks that require interactive back-and-forth with the user
        - Tasks that depend on the result of your current work

        **How it works:**
        1. Call `sessions_spawn` with a clear `task` description
        2. The worker runs independently; you'll be notified when it completes
        3. Results are automatically announced back to the current session
        4. You can continue responding to the user while workers run

        **Important**: Be proactive about using worker tasks. When you identify a task that fits the criteria above, \
        spawn a worker without waiting for explicit instructions. Explain to the user what you've delegated.
        """;

    // 自适应规划协议段落
    private static final String PLANNING_SECTION = """
        ## Planning Protocol (MANDATORY)

        **CRITICAL RULE**: For any task that requires 2 or more tool calls, you MUST first output a plan \
        in plain text BEFORE making any tool calls. Never jump directly into tool execution for complex tasks.

        **When you receive a multi-step request, your FIRST response must be:**
        1. A brief assessment of what's needed
        2. A numbered list of steps you'll take (2-4 bullets)
        3. Any clarifying questions if requirements are ambiguous

        **Only AFTER outputting this plan** should you begin executing with tools.

        **Skip planning for:**
        - Simple questions (factual lookups, explanations)
        - Single-tool operations (read one file, run one command)
        - Follow-up actions in an ongoing task where the plan is already established

        **Key Principle**: Prefer asking one good clarifying question over making wrong assumptions \
        that lead to wasted effort. But don't over-ask — if the intent is reasonably clear, proceed \
        with your plan and start executing.
        """;

    public SystemPromptBuilder(ToolRegistry toolRegistry, SkillIndexBuilder skillIndexBuilder,
                                MemorySearchService memorySearchService,
                                Optional<McpPromptProvider> mcpPromptProvider,
                                SoulConfigService soulConfigService,
                                Optional<HeartbeatConfigService> heartbeatConfigService,
                                Optional<MemoryStore> memoryStore) {
        this.toolRegistry = toolRegistry;
        this.skillIndexBuilder = skillIndexBuilder;
        this.memorySearchService = memorySearchService;
        this.mcpPromptProvider = mcpPromptProvider;
        this.soulConfigService = soulConfigService;
        this.heartbeatConfigService = heartbeatConfigService;
        this.memoryStore = memoryStore;

        this.kernelPromptFacet = new KernelPromptFacet();
        this.soulPromptFacet = new SoulPromptFacet(soulConfigService);
        this.rulePromptFacet = new RulePromptFacet(soulConfigService);
        this.profilePromptFacet = new ProfilePromptFacet(soulConfigService);
        this.toolPromptFacet = new ToolPromptFacet(toolRegistry);
        this.memoryPromptFacet = new MemoryPromptFacet(memorySearchService, memoryStore.orElse(null));
    }

    /**
     * 构建完整的系统提示
     */
    public String build(PromptMode mode) {
        return build(mode, null, null, "main");
    }

    /**
     * 构建系统提示（可指定工具白名单）
     */
    public String build(PromptMode mode, Set<String> allowedTools) {
        return build(mode, allowedTools, null, "main");
    }

    /**
     * 构建系统提示（可指定工具白名单和排除的 MCP 服务器）
     */
    public String build(PromptMode mode, Set<String> allowedTools, Set<String> excludedMcpServers) {
        return build(mode, allowedTools, excludedMcpServers, "main");
    }

    /**
     * 构建系统提示（支持 agentId，用于多 Agent 作用域）
     */
    public String build(PromptMode mode, Set<String> allowedTools, Set<String> excludedMcpServers, String agentId) {
        if (mode == PromptMode.NONE) {
            String name = soulConfigService.extractAgentName(agentId);
            if (name != null && !name.isBlank()) {
                return "You are " + name + ", an AI coding assistant.";
            }
            return "You are an AI coding assistant.";
        }

        PromptAssemblyContext context = new PromptAssemblyContext(
                mode, allowedTools, excludedMcpServers, agentId
        );

        java.util.Map<String, String> blocks = new java.util.HashMap<>();
        blocks.put("IDENTITY", buildIdentitySection(agentId));
        blocks.put("SOUL", soulPromptFacet.supports(context) ? soulPromptFacet.render(context) : "");
        blocks.put("RULE", rulePromptFacet.supports(context) ? rulePromptFacet.render(context) : "");
        blocks.put("PROFILE", profilePromptFacet.supports(context) ? profilePromptFacet.render(context) : "");
        blocks.put("TOOLS", toolPromptFacet.supports(context) ? toolPromptFacet.render(context) : "");
        blocks.put("SAFETY", mode == PromptMode.FULL ? SAFETY_SECTION.trim() + "\n\n" : "");
        blocks.put("PLANNING", mode == PromptMode.FULL ? PLANNING_SECTION.trim() + "\n\n" : "");
        blocks.put("SUBAGENT", mode == PromptMode.FULL && hasSessionsSpawnTool(allowedTools)
                ? SUBAGENT_SECTION.trim() + "\n\n" : "");
        blocks.put("MEMORY", memoryPromptFacet.supports(context) ? memoryPromptFacet.render(context) : "");
        blocks.put("HEARTBEAT", mode == PromptMode.FULL ? buildHeartbeatSection(context.getAgentId()) : "");
        blocks.put("SKILLS", mode == PromptMode.FULL ? buildSkillsSection(context.getAgentId()) : "");
        blocks.put("WORKSPACE", buildWorkspaceSection());
        blocks.put("DATETIME", mode == PromptMode.FULL ? buildDateTimeSection() : "");
        blocks.put("RUNTIME", buildRuntimeSection(mode));
        blocks.put("MCP", mode == PromptMode.FULL ? buildMcpSection(excludedMcpServers) : "");
        blocks.put("CUSTOM", mode == PromptMode.FULL ? buildCustomSection() : "");

        return kernelPromptFacet.assemble(context, blocks).trim();
    }

    /**
     * 检查 sessions_spawn 工具是否可用
     */
    private boolean hasSessionsSpawnTool(Set<String> allowedTools) {
        List<ToolDefinition> tools = toolRegistry.listDefinitions();
        boolean toolExists = tools.stream().anyMatch(t -> "sessions_spawn".equals(t.getName()));
        if (!toolExists) return false;
        // 如果有白名单，检查是否在白名单中
        return allowedTools == null || allowedTools.contains("sessions_spawn");
    }

    /**
     * 构建 Identity 段落，如果 SOUL.md 中没有名字则附加首次对话提醒。
     */
    private String buildIdentitySection(String agentId) {
        String base = IDENTITY_SECTION.trim();
        String name = soulConfigService.extractAgentName(agentId);
        if (name == null || name.isBlank()) {
            return base + NAME_REMINDER_INSTRUCTION.stripTrailing();
        }
        return base;
    }

    /**
     * 构建技能段落
     */
    private String buildSkillsSection(String agentId) {
        String skillIndex = skillIndexBuilder.buildIndex(agentId);

        if (skillIndex.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n\n");
        sb.append("Skills are specialized instruction sets that dramatically improve output quality for specific tasks.\n\n");
        sb.append("**How to use skills:**\n");
        sb.append("1. **Manual trigger**: User types `/skill-name arguments` (e.g., `/frontend-design create a login page`)\n");
        sb.append("2. **Auto trigger**: Call the `use_skill` tool when a task matches an available skill\n\n");
        sb.append("**CRITICAL — Auto-activation rules:**\n");
        sb.append("- **BEFORE** writing code, creating files, or generating content for a task, check if any skill matches\n");
        sb.append("- If a skill matches, call `use_skill(skill_name=\"...\")` FIRST to load expert instructions\n");
        sb.append("- Skills provide specialized workflows and quality standards — always prefer using a matching skill\n");
        sb.append("- Do NOT skip skill activation to save a step — the quality improvement is significant\n\n");

        // 附加技能索引（只有 XML 部分，说明已在上面）
        sb.append(skillIndexBuilder.buildCompactIndex(agentId));
        sb.append("\n\n");

        return sb.toString();
    }

    private String buildMcpSection(Set<String> excludedMcpServers) {
        if (mcpPromptProvider.isEmpty()) {
            return "";
        }
        String mcpAdditions = mcpPromptProvider.get().getSystemPromptAdditions(excludedMcpServers);
        if (mcpAdditions == null || mcpAdditions.isBlank()) {
            return "";
        }
        return mcpAdditions.trim() + "\n\n";
    }

    private String buildCustomSection() {
        if (customSystemPrompt == null || customSystemPrompt.isBlank()) {
            return "";
        }
        return "## Custom Instructions\n\n" + customSystemPrompt.trim() + "\n\n";
    }

    int kernelTemplateBuildCount(PromptMode mode) {
        return kernelPromptFacet.getTemplateBuildCount(mode);
    }

    /**
     * 构建 Heartbeat 段落
     */
    private String buildHeartbeatSection(String agentId) {
        if (heartbeatConfigService.isEmpty()) {
            return "";
        }
        try {
            java.util.Map<String, Object> config = heartbeatConfigService.get().getConfig(agentId);
            Boolean enabled = (Boolean) config.getOrDefault("enabled", true);
            if (!Boolean.TRUE.equals(enabled)) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Heartbeat System\n\n");
            sb.append("The system runs **periodic heartbeat checks** — background Agent executions that review `HEARTBEAT.md` ");
            sb.append("and proactively notify the user when something worth reporting is found.\n\n");
            sb.append("**Your role:**\n");
            sb.append("- Use `update_heartbeat_md` to add or update checklist items in `HEARTBEAT.md`\n");
            sb.append("- Heartbeat changes take effect on the next scheduled cycle\n\n");
            sb.append("**When to call `update_heartbeat_md`:**\n");
            sb.append("- User sets a reminder (\"remind me to...\", \"check X tomorrow\", \"follow up on Y\")\n");
            sb.append("- User mentions a recurring concern or tracking item\n");
            sb.append("- User asks to stop a reminder or remove a checklist item\n");
            sb.append("- The heartbeat checklist is missing something obviously useful\n\n");
            sb.append("**Rules:**\n");
            sb.append("- Always include the `HEARTBEAT_OK` instruction so silent cycles remain silent\n");
            sb.append("- Keep entries concise — the heartbeat Agent reads this file each cycle\n");
            sb.append("- Do NOT update heartbeat just because you did something; only update when the user ");
            sb.append("wants a future follow-up or reminder\n\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to build heartbeat section", e);
            return "";
        }
    }

    /**
     * 构建工作目录段落
     */
    private String buildWorkspaceSection() {
        Path workspacePath = Path.of(workspace).toAbsolutePath().normalize();

        StringBuilder sb = new StringBuilder();
        sb.append("## Workspace\n\n");
        sb.append(String.format("Working directory: `%s`\n\n", workspacePath));
        sb.append("File operations should be relative to this directory unless specified otherwise.\n\n");
        return sb.toString();
    }

    /**
     * 构建日期时间段落
     */
    private String buildDateTimeSection() {
        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.systemDefault();

        StringBuilder sb = new StringBuilder();
        sb.append("## Current Date & Time\n\n");
        sb.append(String.format("- Date: %s\n", now.format(DateTimeFormatter.ISO_LOCAL_DATE)));
        sb.append(String.format("- Time: %s\n", now.format(DateTimeFormatter.ofPattern("HH:mm"))));
        sb.append(String.format("- Timezone: %s\n\n", zoneId.getId()));
        return sb.toString();
    }

    /**
     * 构建运行环境段落
     */
    private String buildRuntimeSection(PromptMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Runtime\n\n");
        sb.append(String.format("- OS: %s\n", System.getProperty("os.name")));
        sb.append(String.format("- Java: %s\n", System.getProperty("java.version")));
        sb.append(String.format("- Mode: %s\n", mode.name().toLowerCase()));
        if (bundledRuntimeEnabled) {
            sb.append("- Bundled Runtime: enabled (prefer bundled Python/Node; do not ask user to install runtimes)\n");
            if (bundledRuntimeHome != null && !bundledRuntimeHome.isBlank()) {
                sb.append(String.format("- Bundled Runtime Home: %s\n", bundledRuntimeHome));
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Prompt 模式
     */
    public enum PromptMode {
        /** 完整提示，包含所有段落 */
        FULL,
        /** 精简提示，用于子代理，省略 Skills、Safety、DateTime */
        MINIMAL,
        /** 技能模式，仅 Identity + Workspace + Runtime，不含工具列表（工具通过 tools 参数传递） */
        SKILL,
        /** 仅身份行 */
        NONE
    }
}
