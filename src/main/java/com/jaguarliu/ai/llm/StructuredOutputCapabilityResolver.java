package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 判断结构化输出是否优先走 provider 原生能力。
 */
@Component
public class StructuredOutputCapabilityResolver {

    private final LlmCapabilityService capabilityService;

    public StructuredOutputCapabilityResolver() {
        this.capabilityService = null;
    }

    @Autowired
    public StructuredOutputCapabilityResolver(LlmCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    public boolean shouldUseNativeStructuredOutput(LlmClient llmClient, LlmRequest request) {
        return request != null
                && request.getStructuredOutput() != null
                && llmClient instanceof OpenAiCompatibleLlmClient
                && capabilityService != null
                && capabilityService.supportsNativeStructuredOutput(request);
    }
}
