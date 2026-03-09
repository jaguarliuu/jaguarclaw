package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM verifier 的结构化判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifierDecision {

    private Boolean terminal;

    private Boolean shouldContinue;

    private String outcome;

    private String failureCategory;

    private String reason;

    private String userMessage;

    private String action;

    private String targetItemId;

    private Double confidence;
}
