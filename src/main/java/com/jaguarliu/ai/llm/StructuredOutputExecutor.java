package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.llm.model.StructuredLlmResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一结构化输出执行入口。
 */
@Component
@RequiredArgsConstructor
public class StructuredOutputExecutor {

    private final LlmClient llmClient;
    private final StructuredOutputService structuredOutputService;
    private final StructuredOutputPromptBuilder promptBuilder;
    private final StructuredOutputCapabilityResolver capabilityResolver;

    public <T> StructuredLlmResult<T> execute(LlmRequest request, Class<T> responseType) {
        if (request == null || request.getStructuredOutput() == null) {
            throw new IllegalArgumentException("Structured output spec is required");
        }

        boolean attemptNative = capabilityResolver.shouldUseNativeStructuredOutput(llmClient, request);
        if (!attemptNative) {
            return executePromptFallback(request, responseType, false);
        }

        try {
            LlmResponse response = llmClient.chat(request);
            T value = structuredOutputService.parse(response.getContent(), responseType);
            return StructuredLlmResult.<T>builder()
                    .value(value)
                    .rawText(response.getContent())
                    .nativeStructuredOutput(true)
                    .fallbackUsed(false)
                    .build();
        } catch (Exception primaryFailure) {
            if (!Boolean.TRUE.equals(request.getStructuredOutput().getFallbackToPromptJson())) {
                throw primaryFailure;
            }
            return executePromptFallback(request, responseType, true);
        }
    }

    private <T> StructuredLlmResult<T> executePromptFallback(LlmRequest request,
                                                             Class<T> responseType,
                                                             boolean fallbackUsed) {
        LlmRequest fallbackRequest = promptBuilder.buildPromptJsonFallbackRequest(request);
        LlmResponse fallbackResponse = llmClient.chat(fallbackRequest);
        T value = structuredOutputService.parse(fallbackResponse.getContent(), responseType);
        return StructuredLlmResult.<T>builder()
                .value(value)
                .rawText(fallbackResponse.getContent())
                .nativeStructuredOutput(false)
                .fallbackUsed(fallbackUsed)
                .build();
    }
}
