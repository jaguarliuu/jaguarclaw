package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;

import java.util.List;

/**
 * 上下文压缩服务接口
 * P1: NoOpContextCompactionService（空实现，直接返回）
 * P2: LlmContextCompactionService（用 LLM 生成摘要压缩早期对话）
 */
public interface ContextCompactionService {

    /**
     * 判断是否需要压缩
     * @param history 当前历史消息
     * @param estimatedTokens 估算的 token 数
     */
    boolean shouldCompact(List<LlmRequest.Message> history, int estimatedTokens);

    /**
     * 执行压缩，返回压缩后的历史消息
     */
    CompactionResult compact(List<LlmRequest.Message> history);

    record CompactionResult(List<LlmRequest.Message> messages, String summary, boolean compacted) {
        static CompactionResult noOp(List<LlmRequest.Message> messages) {
            return new CompactionResult(messages, null, false);
        }
    }
}

