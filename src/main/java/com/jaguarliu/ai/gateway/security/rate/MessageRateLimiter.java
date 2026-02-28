package com.jaguarliu.ai.gateway.security.rate;

import com.jaguarliu.ai.gateway.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息限流（按 connectionId 每分钟）
 */
@Component
@RequiredArgsConstructor
public class MessageRateLimiter {

    private final SecurityProperties securityProperties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean allow(String connectionId) {
        String key = connectionId != null ? connectionId : "unknown";
        int limit = securityProperties.getRateLimit().getMessagePerMinutePerConnection();
        long now = System.currentTimeMillis();
        long windowStart = now / 60_000;

        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(windowStart, 0));
        synchronized (counter) {
            if (counter.windowStart != windowStart) {
                counter.windowStart = windowStart;
                counter.count = 0;
            }
            if (counter.count >= limit) {
                return false;
            }
            counter.count++;
            return true;
        }
    }

    public void clear(String connectionId) {
        if (connectionId != null) {
            counters.remove(connectionId);
        }
    }

    private static class WindowCounter {
        private long windowStart;
        private int count;

        private WindowCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

