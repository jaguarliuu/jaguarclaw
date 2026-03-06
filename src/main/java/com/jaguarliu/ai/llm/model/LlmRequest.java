package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM 请求模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmRequest {

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 模型名称（可选，默认使用配置）
     */
    private String model;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大 token 数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 工具定义列表（OpenAI Function Calling 格式）
     */
    private List<Map<String, Object>> tools;

    /**
     * 工具选择策略：auto / none / required
     */
    private String toolChoice;

    /**
     * Provider ID（可选，用于路由到指定 Provider 的 WebClient）
     */
    private String providerId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        /**
         * 角色：system, user, assistant, tool
         */
        private String role;

        /**
         * 纯文本内容（向后兼容字段）
         */
        private String content;

        /**
         * 结构化内容块（多模态输入）
         */
        private List<ContentPart> parts;

        /**
         * 工具调用列表（仅 assistant 角色有）
         */
        private List<ToolCall> toolCalls;

        /**
         * 工具调用 ID（仅 tool 角色有，用于关联调用）
         */
        private String toolCallId;

        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }

        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }

        public static Message assistant(String content) {
            return Message.builder().role("assistant").content(content).build();
        }

        public static Message userWithParts(String content, List<ContentPart> parts) {
            return Message.builder()
                    .role("user")
                    .content(content)
                    .parts(parts)
                    .build();
        }

        public static Message userWithTextAndImages(String text, List<ImagePart> images) {
            List<ContentPart> parts = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                parts.add(ContentPart.text(text));
            }
            if (images != null) {
                images.stream()
                        .filter(Objects::nonNull)
                        .map(ContentPart::image)
                        .forEach(parts::add);
            }
            return userWithParts(text, parts);
        }

        /**
         * 创建带工具调用的 assistant 消息
         */
        public static Message assistantWithToolCalls(List<ToolCall> toolCalls) {
            return Message.builder()
                    .role("assistant")
                    .toolCalls(toolCalls)
                    .build();
        }

        /**
         * 创建工具结果消息
         */
        public static Message toolResult(String toolCallId, String content) {
            return Message.builder()
                    .role("tool")
                    .toolCallId(toolCallId)
                    .content(content)
                    .build();
        }

        public List<ContentPart> resolvedParts() {
            if (parts != null && !parts.isEmpty()) {
                return parts;
            }
            if (content != null && !content.isBlank()) {
                return List.of(ContentPart.text(content));
            }
            return List.of();
        }

        public String resolvedTextContent() {
            if (content != null) {
                return content;
            }
            String text = resolvedParts().stream()
                    .filter(ContentPart::isText)
                    .map(ContentPart::getText)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse(null);
            return text != null && !text.isBlank() ? text : null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContentPart {
        private String type;
        private String text;
        private ImagePart image;

        public static ContentPart text(String text) {
            return ContentPart.builder()
                    .type("text")
                    .text(text)
                    .build();
        }

        public static ContentPart image(ImagePart image) {
            return ContentPart.builder()
                    .type("image")
                    .image(image)
                    .build();
        }

        public boolean isText() {
            return "text".equals(type);
        }

        public boolean isImage() {
            return "image".equals(type);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImagePart {
        private String filePath;
        private String storagePath;
        private String mimeType;
        private String fileName;
    }
}
