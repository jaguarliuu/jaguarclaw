package com.jaguarliu.ai.nodeconsole;

/**
 * 节点连通性测试结果（连接器层）
 */
public record ConnectionTestOutcome(
        boolean success,
        ExecResult.ErrorType errorType,
        String message
) {

    public static ConnectionTestOutcome ok() {
        return new ConnectionTestOutcome(true, ExecResult.ErrorType.NONE, null);
    }

    public static ConnectionTestOutcome fail(ExecResult.ErrorType errorType, String message) {
        return new ConnectionTestOutcome(false, errorType != null ? errorType : ExecResult.ErrorType.UNKNOWN, message);
    }
}
