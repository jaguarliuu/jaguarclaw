package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上下文压缩默认空实现
 * P1 使用此实现（不压缩）。
 * P2 实现时创建新类并标 @Primary 覆盖此 Bean。
 */
@Component
public class NoOpContextCompactionService implements ContextCompactionService {

    @Override
    public boolean shouldCompact(List<LlmRequest.Message> history, int estimatedTokens) {
        return false;
    }

    @Override
    public CompactionResult compact(List<LlmRequest.Message> history) {
        return CompactionResult.noOp(history);
    }
}

