package com.jaguarliu.ai.tools.builtin.skill;

import com.jaguarliu.ai.skills.registry.SkillRegistry;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Skill 文件保存工具
 *
 * 允许在 userSkillsDir (~/.jaguarclaw/skills/<skill_name>/) 中创建或更新 skill 文件，
 * 绕过 workspace 写入限制，供 skill-creator 等工作流使用。
 * 保存 SKILL.md 后自动刷新 SkillRegistry。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaveSkillFileTool implements Tool {

    private final SkillRegistry skillRegistry;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("save_skill_file")
                .description("在用户 skill 目录（~/.jaguarclaw/skills/<skill_name>/）中创建或更新文件。"
                        + "用于创建新 skill 或更新现有 skill 的文件（SKILL.md、scripts/、references/ 等）。"
                        + "保存 SKILL.md 后自动刷新 skill 注册表使其立即生效。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "skill_name", Map.of("type", "string",
                                        "description", "Skill 名称（kebab-case，对应目录名，如 'my-skill'）"),
                                "relative_path", Map.of("type", "string",
                                        "description", "相对于 skill 目录的文件路径，如 'SKILL.md'、'scripts/helper.py'、'references/api.md'"),
                                "content", Map.of("type", "string",
                                        "description", "文件内容")
                        ),
                        "required", List.of("skill_name", "relative_path", "content")
                ))
                .hitl(false)
                .tags(List.of("skill", "write"))
                .riskLevel("medium")
                .parameterSummary("skill_name (required) | relative_path (required) | content (required)")
                .example("save_skill_file({ skill_name: 'my-skill', relative_path: 'SKILL.md', content: '---\\nname: my-skill\\n...' })")
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String skillName = (String) arguments.get("skill_name");
            String relativePath = (String) arguments.get("relative_path");
            String content = (String) arguments.get("content");

            if (skillName == null || skillName.isBlank()) {
                return ToolResult.error("Missing required parameter: skill_name");
            }
            if (relativePath == null || relativePath.isBlank()) {
                return ToolResult.error("Missing required parameter: relative_path");
            }
            if (content == null) {
                return ToolResult.error("Missing required parameter: content");
            }

            // 安全检查：禁止路径穿越
            Path skillDir = skillRegistry.getUserSkillsDir().resolve(skillName).normalize();
            Path targetFile = skillDir.resolve(relativePath).normalize();
            if (!targetFile.startsWith(skillDir)) {
                log.warn("save_skill_file path traversal attempt: skill={}, path={}", skillName, relativePath);
                return ToolResult.error("Access denied: path traversal is not allowed");
            }

            try {
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, content, StandardCharsets.UTF_8);
                log.info("Saved skill file: {} ({} bytes)", targetFile, content.length());

                // 写入 SKILL.md 后刷新注册表，使新 skill 立即可用
                String fileName = targetFile.getFileName().toString();
                if ("SKILL.md".equals(fileName)) {
                    skillRegistry.refresh();
                    log.info("Skill registry refreshed after saving SKILL.md for '{}'", skillName);
                }

                return ToolResult.success("Saved " + content.length() + " bytes to " + targetFile);
            } catch (IOException e) {
                log.error("Failed to save skill file: {}", targetFile, e);
                return ToolResult.error("Failed to save skill file: " + e.getMessage());
            }
        });
    }
}
