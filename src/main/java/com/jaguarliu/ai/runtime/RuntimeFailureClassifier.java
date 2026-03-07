package com.jaguarliu.ai.runtime;

import java.util.Locale;

/**
 * 高置信度失败信号分类器。
 */
final class RuntimeFailureClassifier {

    private RuntimeFailureClassifier() {
    }

    static String inferFailureCategory(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String normalized = content.toLowerCase(Locale.ROOT);
        if (containsAny(normalized,
                "command not found",
                "not installed",
                "missing ",
                "permission denied",
                "access denied",
                "login required",
                "file not found",
                "系统找不到指定的文件",
                "不是内部或外部命令",
                "用提供的模式无法找到文件")) {
            return "environment_missing";
        }

        if (containsAny(normalized,
                "paid plan",
                "subscription",
                "requires payment",
                "requires paid",
                "需要付费",
                "需付费")) {
            return "user_decision_required";
        }

        return null;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
