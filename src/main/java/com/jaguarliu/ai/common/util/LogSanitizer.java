package com.jaguarliu.ai.common.util;

/**
 * 日志脱敏工具
 */
public class LogSanitizer {

    /**
     * 脱敏 endpoint（移除 URL 中的敏感参数）
     */
    public static String sanitizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        // 移除 key=xxx, token=xxx, secret=xxx 等敏感参数
        return endpoint
                .replaceAll("([?&])(key|token|secret|password|apikey|api_key)=[^&]*", "$1$2=***")
                .replaceAll("^key=[^&]*", "key=***");
    }

    /**
     * 脱敏 API Key（只显示前4位和后4位）
     */
    public static String sanitizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return "***";
        }
        return apiKey.substring(0, 4) + "***" + apiKey.substring(apiKey.length() - 4);
    }
}
