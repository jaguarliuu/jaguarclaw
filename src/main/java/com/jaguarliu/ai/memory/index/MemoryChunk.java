package com.jaguarliu.ai.memory.index;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆文本块
 * Chunking 产出的最小检索单元
 *
 * 渐进式加载支持：
 * - P0: 核心事实（用户偏好、项目上下文）— 始终注入 L1
 * - P1: 重要记忆 — 按需注入 L1
 * - P2: 一般记忆 — 搜索时加载
 */
@Data
@Builder
public class MemoryChunk {
    /** 来源文件（相对于 memory 目录） */
    private String filePath;
    /** 起始行号（1-based） */
    private int lineStart;
    /** 结束行号（1-based，含） */
    private int lineEnd;
    /** chunk 原文 */
    private String content;
    
    // ==================== 渐进式加载字段 ====================
    
    /**
     * 优先级：P0（核心）/ P1（重要）/ P2（一般）
     */
    @Builder.Default
    private String priority = "P2";
    
    /**
     * 标题/摘要（L0/L1）
     */
    private String title;
    
    /**
     * 关键点摘要（L1）
     */
    private String summary;

    // ==================== Token 估算 ====================
    
    /**
     * 估算 L0 tokens（标题）
     */
    public int estimateL0Tokens() {
        if (title != null && !title.isEmpty()) {
            return Math.max(title.length() / 4, 15);
        }
        // 无标题时用内容前 50 字符
        if (content != null) {
            int len = Math.min(content.length(), 50);
            return Math.max(len / 4, 15);
        }
        return 15;
    }
    
    /**
     * 估算 L1 tokens（标题 + 摘要）
     */
    public int estimateL1Tokens() {
        int tokens = estimateL0Tokens();
        if (summary != null) tokens += summary.length() / 4;
        return Math.max(tokens, 50);
    }
    
    /**
     * 估算 L2 tokens（完整内容）
     */
    public int estimateL2Tokens() {
        if (content == null) return 0;
        
        int chineseCount = 0;
        int otherCount = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return (int) (chineseCount * 2 + otherCount * 0.3);
    }
}
