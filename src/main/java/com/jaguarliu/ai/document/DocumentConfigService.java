package com.jaguarliu.ai.document;

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
            "你是一位专业写作助手，帮助用户在文档编辑器中进行写作。\n" +
            "你可以使用以下工具：\n" +
            "- doc_read: 读取当前文档内容\n" +
            "- doc_insert: 向文档中插入内容（会实时显示在编辑器中）\n" +
            "- http_get: 获取网页内容（仅用于用户提供的内网链接）\n\n" +
            "写作原则：\n" +
            "1. 保持文风一致，续写时衔接自然\n" +
            "2. 使用 doc_insert 逐段插入内容，不要一次性插入过多\n" +
            "3. 需要了解文档现状时先调用 doc_read\n" +
            "4. 不使用任何其他工具，不执行任何系统命令";

    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private Path configFilePath() {
        return Path.of(System.getProperty("user.home"), ".jaguarclaw", "document-writer-config.json");
    }

    public String getSystemPrompt() {
        if (cache.containsKey("systemPrompt")) return cache.get("systemPrompt");
        try {
            Path p = configFilePath();
            if (Files.exists(p)) {
                Map<?, ?> data = objectMapper.readValue(p.toFile(), Map.class);
                String prompt = (String) data.get("systemPrompt");
                if (prompt != null) { cache.put("systemPrompt", prompt); return prompt; }
            }
        } catch (IOException e) {
            log.warn("Failed to read document-writer config: {}", e.getMessage());
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    public void setSystemPrompt(String prompt) {
        cache.put("systemPrompt", prompt);
        try {
            Path p = configFilePath();
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), Map.of("systemPrompt", prompt));
        } catch (IOException e) {
            log.error("Failed to persist document-writer config: {}", e.getMessage());
        }
    }
}
