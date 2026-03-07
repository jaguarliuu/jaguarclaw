package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化输出调用结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredLlmResult<T> {

    private T value;

    private String rawText;

    private Boolean nativeStructuredOutput;

    private Boolean fallbackUsed;
}
