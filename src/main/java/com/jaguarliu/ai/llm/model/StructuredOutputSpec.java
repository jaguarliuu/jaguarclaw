package com.jaguarliu.ai.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化输出规范。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructuredOutputSpec {

    private String name;

    private Map<String, Object> jsonSchema;

    private Boolean strict;

    private Boolean fallbackToPromptJson;
}
