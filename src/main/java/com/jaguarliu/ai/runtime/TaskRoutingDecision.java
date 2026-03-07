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

    private Boolean shouldUseTools;

    private Boolean shouldUseStrategy;

    private RunOutcomeStatus outcomeStatus;

    private String outcomeMessage;

    private String outcomeDetail;

    private String reason;

    private Double confidence;

    public boolean blocked() {
        return routeMode == TaskRouteMode.BLOCKED || outcomeStatus != null;
    }

    public RunOutcome toOutcome() {
        if (outcomeStatus == null) {
            return null;
        }
        return new RunOutcome(outcomeStatus, outcomeMessage, outcomeDetail);
    }

    public TaskContract toTaskContract(String goal) {
        TaskComplexity resolvedComplexity = complexity != null ? complexity : defaultComplexity(routeMode);
        return new TaskContract(
                goal,
                resolvedComplexity,
                routeMode == TaskRouteMode.HEAVY || resolvedComplexity == TaskComplexity.EXTERNAL_DEPENDENCY,
                outcomeStatus == RunOutcomeStatus.BLOCKED_PENDING_USER_DECISION
        );
    }

    private TaskComplexity defaultComplexity(TaskRouteMode mode) {
        if (mode == null) {
            return TaskComplexity.HEAVY;
        }
        return switch (mode) {
            case CHAT, DIRECT -> TaskComplexity.DIRECT;
            case LIGHT -> TaskComplexity.LIGHT;
            case HEAVY -> TaskComplexity.HEAVY;
            case BLOCKED -> TaskComplexity.EXTERNAL_DEPENDENCY;
        };
    }
}
