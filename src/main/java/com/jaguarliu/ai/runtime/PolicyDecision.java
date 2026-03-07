package com.jaguarliu.ai.runtime;

/**
 * Policy 层的决策结果。
 */
public record PolicyDecision(
        TaskComplexity complexity,
        boolean enterHeavyLoop,
        RunOutcome outcome,
        TaskContract contract
) {

    public static PolicyDecision direct(String goal) {
        return new PolicyDecision(
                TaskComplexity.DIRECT,
                false,
                null,
                new TaskContract(goal, TaskComplexity.DIRECT, false, false)
        );
    }

    public static PolicyDecision light(String goal) {
        return new PolicyDecision(
                TaskComplexity.LIGHT,
                false,
                null,
                new TaskContract(goal, TaskComplexity.LIGHT, false, false)
        );
    }

    public static PolicyDecision heavy(String goal) {
        return new PolicyDecision(
                TaskComplexity.HEAVY,
                true,
                null,
                new TaskContract(goal, TaskComplexity.HEAVY, true, false)
        );
    }

    public static PolicyDecision external(String goal) {
        return new PolicyDecision(
                TaskComplexity.EXTERNAL_DEPENDENCY,
                true,
                null,
                new TaskContract(goal, TaskComplexity.EXTERNAL_DEPENDENCY, true, false)
        );
    }

    public static PolicyDecision blocked(TaskComplexity complexity, RunOutcome outcome, String goal) {
        return new PolicyDecision(
                complexity,
                false,
                outcome,
                new TaskContract(goal, complexity, true, true)
        );
    }
}
