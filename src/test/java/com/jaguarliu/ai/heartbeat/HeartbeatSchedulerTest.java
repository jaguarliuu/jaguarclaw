package com.jaguarliu.ai.heartbeat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatSchedulerTest {

    @Test
    void shouldTreatHeartbeatOkAsSilentEvenWhenNotAtPrefix() {
        boolean silent = HeartbeatScheduler.isSilentHeartbeatResponse(
                "今天暂无事项。HEARTBEAT_OK",
                300
        );

        assertTrue(silent);
    }

    @Test
    void shouldTreatHeartbeatOkInCodeFenceAsSilent() {
        boolean silent = HeartbeatScheduler.isSilentHeartbeatResponse(
                "```text\nHEARTBEAT_OK\n```",
                300
        );

        assertTrue(silent);
    }

    @Test
    void shouldForceNotifyWhenNotifyTokenExists() {
        boolean silent = HeartbeatScheduler.isSilentHeartbeatResponse(
                "HEARTBEAT_NOTIFY: 请提醒用户今晚 9 点前提交日报。",
                300
        );

        assertFalse(silent);
    }

    @Test
    void shouldNotifyForRegularMessageWithoutSilentSignal() {
        boolean silent = HeartbeatScheduler.isSilentHeartbeatResponse(
                "请提醒用户：今天 18:00 前有一个待办未完成。",
                300
        );

        assertFalse(silent);
    }
}

