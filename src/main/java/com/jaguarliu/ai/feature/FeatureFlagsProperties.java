package com.jaguarliu.ai.feature;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 2.0.2 多 Agent 相关功能开关。
 *
 * 说明：
 * - 用于灰度发布与紧急回滚。
 * - 默认全开；可按模块关闭并回退到兼容行为。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "feature")
public class FeatureFlagsProperties {

    /** Agent 控制面（agent.list/create/get/update/delete） */
    private boolean agentControlPlane = true;

    /** Agent 作用域 Prompt（Kernel + Facets） */
    private boolean agentScopedPrompt = true;

    /** 双层记忆（GLOBAL + AGENT） */
    private boolean agentDualMemory = true;

    /** MCP/Skill 的作用域（global + agent） */
    private boolean agentScopedMcpSkill = true;

    /** @mention 路由到指定 Agent */
    private boolean agentMentionRouting = true;
}
