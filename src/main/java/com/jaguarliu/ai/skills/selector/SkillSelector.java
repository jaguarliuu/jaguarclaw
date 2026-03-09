package com.jaguarliu.ai.skills.selector;

import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 选择器。
 *
 * 当前仅负责解析用户显式输入的 slash command：`/skill-name args`。
 */
@Slf4j
@Service
public class SkillSelector {

    /**
     * 匹配 /skill-name 或 /skill-name args
     * 组1: skill 名称
     * 组2: 参数（可选）
     */
    private static final Pattern SLASH_COMMAND = Pattern.compile(
            "^/(\\S+)(?:\\s+(.*))?$",
            Pattern.DOTALL
    );

    private final SkillRegistry registry;

    public SkillSelector(SkillRegistry registry) {
        this.registry = registry;
    }

    /**
     * 尝试解析手动触发的 skill（/skill-name args）
     *
     * @param userInput 用户输入
     * @return 选择结果
     */
    public SkillSelection tryManualSelection(String userInput) {
        return tryManualSelection(userInput, "main");
    }

    /**
     * 按 agent 作用域尝试解析手动触发的 skill（/skill-name args）
     */
    public SkillSelection tryManualSelection(String userInput, String agentId) {
        if (userInput == null || userInput.isBlank()) {
            return SkillSelection.none(userInput);
        }

        String trimmed = userInput.trim();

        if (!trimmed.startsWith("/")) {
            return SkillSelection.none(userInput);
        }

        Matcher matcher = SLASH_COMMAND.matcher(trimmed);
        if (!matcher.matches()) {
            return SkillSelection.none(userInput);
        }

        String skillName = matcher.group(1);
        String arguments = matcher.group(2);

        if (!registry.isAvailable(skillName, agentId)) {
            log.warn("Skill not found or unavailable: {}", skillName);
            return SkillSelection.none(userInput);
        }

        log.info("Manual skill selection: {} (args: {})",
                skillName, arguments != null ? arguments.substring(0, Math.min(50, arguments.length())) : "none");

        return SkillSelection.manual(skillName, arguments, userInput);
    }

    /**
     * 检查用户输入是否为 slash command 格式
     * （不检查 skill 是否存在）
     */
    public boolean isSlashCommand(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        return SLASH_COMMAND.matcher(userInput.trim()).matches();
    }

    /**
     * 从 slash command 中提取 skill 名称
     * （不检查 skill 是否存在）
     */
    public String extractSkillName(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        Matcher matcher = SLASH_COMMAND.matcher(userInput.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
