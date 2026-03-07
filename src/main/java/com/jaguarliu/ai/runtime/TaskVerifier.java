package com.jaguarliu.ai.runtime;

import java.util.List;

/**
 * 任务外部验证器。
 */
public interface TaskVerifier {

    VerificationResult verify(RunContext context, String assistantReply, List<String> observations);
}
