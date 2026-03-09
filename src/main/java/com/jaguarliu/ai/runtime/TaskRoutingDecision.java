package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顶层任务路由决策。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRoutingDecision {

    private TaskRouteMode routeMode;

    private TaskComplexity complexity;

    private String reason;

    private Double confidence;

    public boolean blocked() {
        return false;
    }

    public RunOutcome toOutcome() {
        return null;
    }

    public TaskContract toTaskContract(String goal) {
        TaskComplexity resolvedComplexity = complexity != null ? complexity : defaultComplexity(routeMode);
        return new TaskContract(
                goal,
                resolvedComplexity,
                routeMode == TaskRouteMode.REACT,
                false
        );
    }

    private TaskComplexity defaultComplexity(TaskRouteMode mode) {
        if (mode == null) {
            return TaskComplexity.HEAVY;
        }
        return switch (mode) {
            case DIRECT -> TaskComplexity.DIRECT;
            case REACT -> TaskComplexity.HEAVY;
        };
    }
}
