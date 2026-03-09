package com.jaguarliu.ai.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProgressSnapshot Tests")
class ProgressSnapshotTest {

    @Test
    @DisplayName("should keep repairable failures within repair budget")
    void shouldKeepRepairableFailuresWithinRepairBudget() {
        ProgressSnapshot snapshot = new ProgressSnapshot(
                2,
                RuntimeFailureCategories.REPAIRABLE_ENVIRONMENT,
                0,
                1,
                0
        );

        assertTrue(snapshot.hasRepairBudgetRemaining(2));
        assertFalse(snapshot.shouldStopForRepeatedFailures(2, 2));
    }

    @Test
    @DisplayName("should stop repeated non repairable failures")
    void shouldStopRepeatedNonRepairableFailures() {
        ProgressSnapshot snapshot = new ProgressSnapshot(
                2,
                RuntimeFailureCategories.TOOL_ERROR,
                0,
                0,
                0
        );

        assertTrue(snapshot.shouldStopForRepeatedFailures(2, 2));
    }
}
