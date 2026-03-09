package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlanItem {
    private String id;
    private String title;
    private PlanItemStatus status;
    private PlanExecutionMode executionMode;
    private String notes;
    private String subRunId;
}
