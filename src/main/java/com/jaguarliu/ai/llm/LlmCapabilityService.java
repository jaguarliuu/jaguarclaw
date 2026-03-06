package com.jaguarliu.ai.llm;

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
}
