package com.jaguarliu.ai.runtime;

/**
 * 将结构化终态渲染为面向用户的最终消息。
 */
public final class RunOutcomeMessageFormatter {

    private RunOutcomeMessageFormatter() {
    }

    public static String render(RunOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        String detail = normalize(outcome.detail());
        String message = normalize(outcome.message());
        return switch (outcome.status()) {
            case COMPLETED -> firstNonBlank(detail, message, "Done.");
            case COMPLETED_WITH_DEGRADATION -> appendDetail(
                    "I completed as much as I could, but there were some limitations.",
                    chooseSupplement(outcome.status(), detail, message)
            );
            case BLOCKED_BY_ENVIRONMENT -> appendDetail(
                    "I couldn't continue because the current environment blocked this action.",
                    chooseSupplement(outcome.status(), detail, message)
            );
            case BLOCKED_PENDING_USER_DECISION -> appendDetail(
                    "I need your confirmation before I can continue.",
                    chooseSupplement(outcome.status(), detail, message)
            );
            case NOT_WORTH_CONTINUING -> appendDetail(
                    "I stopped here because continuing automatically is unlikely to help.",
                    chooseSupplement(outcome.status(), detail, message)
            );
            case FAILED_UNEXPECTEDLY -> appendDetail(
                    "I ran into an unexpected problem and had to stop.",
                    chooseSupplement(outcome.status(), detail, message)
            );
        };
    }

    public static String renderTimeout(String detail) {
        return appendDetail(
                "I had to stop because the run timed out before I could finish.",
                normalize(detail)
        );
    }

    public static String renderUnexpectedFailure(String detail) {
        return appendDetail(
                "I ran into an unexpected internal error and had to stop.",
                normalize(detail)
        );
    }

    private static String chooseSupplement(RunOutcomeStatus status, String detail, String message) {
        if (detail != null) {
            return detail;
        }
        if (message == null) {
            return null;
        }
        String defaultMessage = defaultMessage(status);
        if (defaultMessage != null && defaultMessage.equalsIgnoreCase(message)) {
            return null;
        }
        return message;
    }

    private static String defaultMessage(RunOutcomeStatus status) {
        return switch (status) {
            case COMPLETED -> null;
            case COMPLETED_WITH_DEGRADATION -> "Task completed with degradation";
            case BLOCKED_BY_ENVIRONMENT -> "Task blocked by environment";
            case BLOCKED_PENDING_USER_DECISION -> "Task blocked pending user decision";
            case NOT_WORTH_CONTINUING -> "Task is not worth continuing";
            case FAILED_UNEXPECTEDLY -> "Task failed unexpectedly";
        };
    }

    private static String appendDetail(String prefix, String detail) {
        if (detail == null) {
            return prefix;
        }
        if (prefix.equals(detail)) {
            return prefix;
        }
        return prefix + " Details: " + detail;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
