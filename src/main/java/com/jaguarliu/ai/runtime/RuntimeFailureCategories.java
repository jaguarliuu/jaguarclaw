package com.jaguarliu.ai.runtime;

/**
 * 运行时失败类别常量。
 */
public final class RuntimeFailureCategories {

    public static final String REPAIRABLE_ENVIRONMENT = "repairable_environment";
    public static final String HARD_ENVIRONMENT_BLOCK = "hard_environment_block";
    public static final String USER_DECISION_REQUIRED = "user_decision_required";
    public static final String POLICY_BLOCK = "policy_block";
    public static final String HITL_REJECTED = "hitl_rejected";
    public static final String TOOL_ERROR = "tool_error";

    private RuntimeFailureCategories() {
    }
}
