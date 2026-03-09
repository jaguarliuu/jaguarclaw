package com.jaguarliu.ai.runtime;

/**
 * @deprecated 文本型失败分类器已退出主运行链路。
 * 请优先使用工具层提供的结构化 failureCategory，
 * 并由 LLM verifier 结合运行时上下文做语义判断。
 */
@Deprecated(forRemoval = false)
final class RuntimeFailureClassifier {

    private RuntimeFailureClassifier() {
    }

    static String inferFailureCategory(String content) {
        return null;
    }
}
