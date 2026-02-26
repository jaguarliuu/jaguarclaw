package com.jaguarliu.ai.skills.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 条目
 * SkillRegistry 中存储的完整对象，包含元数据 + 可用性状态 + 成本信息
 *
 * 生命周期：
 * 1. Discovery 阶段：解析 SKILL.md → 得到 SkillMetadata
 * 2. Gating 阶段：检查 requires → 设置 available / unavailableReason
 * 3. Indexing 阶段：计算 tokenCost → 用于 budget 控制
 *
 * 渐进式加载支持：
 * - L0: name + description + tags (~20-50 tokens)
 * - L1: L0 + usageSummary + example (~100-300 tokens)
 * - L2: 完整 body（激活时加载）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEntry {

    /**
     * 元数据（从 YAML frontmatter 解析）
     */
    private SkillMetadata metadata;

    /**
     * 是否可用（gating 结果）
     */
    private boolean available;

    /**
     * 不可用原因
     */
    private String unavailableReason;

    /**
     * 文件最后修改时间
     */
    private long lastModified;

    /**
     * 预估 token 成本（索引部分）
     */
    private int tokenCost;

    // ==================== 渐进式加载字段 ====================

    /**
     * 用法摘要（L1 字段）
     */
    private String usageSummary;

    /**
     * 使用示例（L1 字段）
     */
    private String example;

    // ==================== Token 估算 ====================

    /**
     * 估算 L0 tokens（名称 + 描述）
     */
    public int estimateL0Tokens() {
        int tokens = 20;
        if (metadata != null) {
            tokens += estimateTokens(metadata.getName());
            tokens += estimateTokens(metadata.getDescription());
        }
        return Math.max(tokens, 30);
    }

    /**
     * 估算 L1 tokens（L0 + 用法摘要 + 示例）
     */
    public int estimateL1Tokens() {
        int tokens = estimateL0Tokens();
        if (usageSummary != null) tokens += estimateTokens(usageSummary);
        if (example != null) tokens += estimateTokens(example);
        return Math.max(tokens, 100);
    }

    /**
     * 计算索引 token 成本（兼容旧方法）
     */
    public static int calculateTokenCost(SkillMetadata metadata) {
        return new SkillEntry(metadata, true, null, 0, 0, null, null).estimateL0Tokens();
    }

    /**
     * 简单 token 估算
     */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return (int) (chineseCount * 2 + otherCount * 0.3);
    }
}
