package com.jaguarliu.ai.llm;

/**
 * 结构化输出解析异常。
 */
public class StructuredOutputException extends RuntimeException {

    public StructuredOutputException(String message) {
        super(message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
