package com.jaguarliu.ai.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentConfigService {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一位专业写作助手，帮助用户完成续写、润色、改写、总结等写作任务。\n" +
            "【规则】\n" +
            "1. 直接输出正文内容，不要任何前言、解释或元说明（如\"好的\"\"当然\"\"以下是...\"）\n" +
            "2. 使用 Markdown 格式：标题、加粗、列表、代码块等\n" +
            "3. 保持与原文风格一致，续写时自然衔接\n" +
            "4. 输出完整内容，不截断";

    private static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private Path configFilePath() {
        return Path.of(System.getProperty("user.home"), ".jaguarclaw", "document-writer-config.json");
    }

    public String getSystemPrompt() {
        if (cache.containsKey(KEY_SYSTEM_PROMPT)) return cache.get(KEY_SYSTEM_PROMPT);
        try {
            Path p = configFilePath();
            if (Files.exists(p)) {
                Map<String, String> data = objectMapper.readValue(p.toFile(), CONFIG_TYPE);
                String prompt = data.get(KEY_SYSTEM_PROMPT);
                if (prompt != null) { cache.put(KEY_SYSTEM_PROMPT, prompt); return prompt; }
            }
        } catch (IOException e) {
            log.warn("Failed to read document-writer config: {}", e.getMessage());
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    public void setSystemPrompt(String prompt) {
        cache.put(KEY_SYSTEM_PROMPT, prompt);
        try {
            Path p = configFilePath();
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), Map.of(KEY_SYSTEM_PROMPT, prompt));
        } catch (IOException e) {
            log.error("Failed to persist document-writer config: {}", e.getMessage());
            throw new RuntimeException("Failed to persist document-writer config", e);
        }
    }
}
