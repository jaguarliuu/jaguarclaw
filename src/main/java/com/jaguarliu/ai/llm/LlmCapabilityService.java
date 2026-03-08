package com.jaguarliu.ai.llm;

import com.jaguarliu.ai.llm.model.LlmRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LlmCapabilityService {

    private final LlmProperties properties;

    public boolean supportsVision(String modelSelection) {
        String providerId = null;
        String modelName = null;

        if (modelSelection != null && modelSelection.contains(":")) {
            String[] parts = modelSelection.split(":", 2);
            providerId = parts[0];
            modelName = parts[1];
        } else if (properties.getDefaultModel() != null && properties.getDefaultModel().contains(":")) {
            String[] parts = properties.getDefaultModel().split(":", 2);
            providerId = parts[0];
            modelName = parts[1];
        } else {
            modelName = properties.getModel();
        }

        if (modelName == null || modelName.isBlank()) {
            return false;
        }

        if (providerId != null) {
            var provider = properties.getProvider(providerId);
            if (provider != null && provider.getVisionModels() != null && provider.getVisionModels().contains(modelName)) {
                return true;
            }
        }

        String value = modelName.toLowerCase(Locale.ROOT);
        List<String> patterns = List.of(
                "gpt-4o", "gpt-4.1", "gpt-4-turbo", "o1", "o3", "o4",
                "claude-3", "claude-sonnet-4", "claude-opus-4",
                "gemini", "qwen-vl", "qvq", "llava", "minicpm-v",
                "internvl", "glm-4v", "step-1v", "pixtral", "vision"
        );
        return patterns.stream().anyMatch(value::contains);
    }

    public boolean supportsNativeStructuredOutput(LlmRequest request) {
        ResolvedModel resolved = resolveRequestModel(request);
        if (resolved.modelName == null || resolved.modelName.isBlank()) {
            return false;
        }

        if (resolved.providerId != null) {
            var provider = properties.getProvider(resolved.providerId);
            if (provider != null && provider.getStructuredOutputModels() != null
                    && provider.getStructuredOutputModels().contains(resolved.modelName)) {
                return true;
            }
        }

        String provider = resolved.providerId != null ? resolved.providerId.toLowerCase(Locale.ROOT) : "";
        String model = resolved.modelName.toLowerCase(Locale.ROOT);

        if ("openai".equals(provider) || "default".equals(provider) || provider.isBlank()) {
            return List.of(
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4.1",
                    "gpt-4.1-mini",
                    "gpt-4.1-nano",
                    "o1",
                    "o3",
                    "o4"
            ).stream().anyMatch(model::contains);
        }

        return false;
    }

    private ResolvedModel resolveRequestModel(LlmRequest request) {
        String providerId = request != null ? request.getProviderId() : null;
        String modelName = request != null ? request.getModel() : null;

        if ((providerId == null || providerId.isBlank() || modelName == null || modelName.isBlank())
                && properties.getDefaultModel() != null && properties.getDefaultModel().contains(":")) {
            String[] parts = properties.getDefaultModel().split(":", 2);
            if (providerId == null || providerId.isBlank()) {
                providerId = parts[0];
            }
            if (modelName == null || modelName.isBlank()) {
                modelName = parts[1];
            }
        }

        if (modelName == null || modelName.isBlank()) {
            modelName = properties.getModel();
        }

        return new ResolvedModel(providerId, modelName);
    }

    private record ResolvedModel(String providerId, String modelName) {}

}
