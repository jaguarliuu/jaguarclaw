package com.jaguarliu.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具定义
 * 描述一个工具的元数据，供 LLM Function Calling 使用
 * 
 * 支持渐进式加载（L0/L1/L2）：
 * - L0: 名称 + 描述 + 标签 + 风险等级 (~20-50 tokens)
 * - L1: L0 + 参数摘要 + 示例 (~100-300 tokens)
 * - L2: 完整 JSON Schema (~500-5000 tokens)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /**
     * 工具名称（唯一标识）
     */
    private String name;

    /**
     * 工具描述（给 LLM 看，说明工具用途）
     */
    private String description;

    /**
     * 参数定义（JSON Schema 格式）
     */
    private Map<String, Object> parameters;

    /**
     * 是否需要 HITL（Human-in-the-Loop）确认
     */
    @Builder.Default
    private boolean hitl = false;

    /**
     * 是否仅在 Skill 激活时可见（skill-scoped）。
     * true 表示该工具不会默认注入到 LLM 提示词中；
     * 只有当某个 skill 的 allowed-tools 列表明确包含此工具时才可见。
     */
    @Builder.Default
    private boolean skillScopedOnly = false;

    /**
     * 工具是否会产出文件（写入磁盘）。
     * true 表示成功执行后应发布 FILE_CREATED 事件，
     * 让前端为该文件渲染下载按钮。
     */
    @Builder.Default
    private boolean producesFile = false;

    // ==================== 渐进式加载字段 ====================

    /**
     * 工具标签（用于相关性匹配）
     * L0 字段
     */
    private List<String> tags;

    /**
     * 风险等级：low / medium / high / critical
     * L0 字段
     */
    @Builder.Default
    private String riskLevel = "low";

    /**
     * 参数摘要（简短描述参数用途）
     * L1 字段
     */
    private String parameterSummary;

    /**
     * 使用示例
     * L1 字段
     */
    private String example;

    // ==================== Token 估算 ====================

    /**
     * 估算 L0 tokens（名称 + 描述）
     */
    public int estimateL0Tokens() {
        int tokens = 0;
        if (name != null) tokens += name.length() / 4;
        if (description != null) tokens += description.length() / 4;
        return Math.max(tokens, 20);
    }

    /**
     * 估算 L1 tokens（L0 + 参数摘要 + 示例）
     */
    public int estimateL1Tokens() {
        int tokens = estimateL0Tokens();
        if (parameterSummary != null) tokens += parameterSummary.length() / 4;
        if (example != null) tokens += example.length() / 4;
        return Math.max(tokens, 100);
    }

    // ==================== OpenAI 格式转换 ====================

    /**
     * L0 格式（只有名称 + 描述，~20-50 tokens）
     * 默认注入，不包含参数 schema
     */
    public Map<String, Object> toOpenAiFormatL0() {
        String desc = description != null ? description : "";
        if (tags != null && !tags.isEmpty()) {
            desc = desc + " [tags: " + String.join(",", tags) + "]";
        }
        String finalDesc = desc;
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", finalDesc
                )
        );
    }

    /**
     * L1 格式（名称 + 描述 + 参数摘要，~100-300 tokens）
     * 按需注入，包含参数概述但不包含完整 schema
     */
    public Map<String, Object> toOpenAiFormatL1() {
        StringBuilder desc = new StringBuilder(description != null ? description : "");
        if (parameterSummary != null && !parameterSummary.isEmpty()) {
            desc.append("\n\n").append(parameterSummary);
        }
        if (example != null && !example.isEmpty()) {
            desc.append("\n\nExample: ").append(example);
        }

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", desc.toString(),
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "_summary", parameterSummary != null ? parameterSummary : ""
                        )
                )
        );
    }

    /**
     * L2 格式（完整 Schema，~500-5000 tokens）
     * 实际执行时加载
     */
    public Map<String, Object> toOpenAiFormat() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description != null ? description : "",
                        "parameters", parameters != null ? parameters : Map.of("type", "object", "properties", Map.of())
                )
        );
    }
}
