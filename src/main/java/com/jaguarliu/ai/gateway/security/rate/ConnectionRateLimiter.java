package com.jaguarliu.ai.gateway.security.rate;

import com.jaguarliu.ai.gateway.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 建连限流（按 IP 每分钟）
 */
@Component
@RequiredArgsConstructor
public class ConnectionRateLimiter {

    private final SecurityProperties securityProperties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean allow(String ip) {
        String key = ip != null ? ip : "unknown";
        int limit = securityProperties.getRateLimit().getConnectionPerMinutePerIp();
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

    private static class WindowCounter {
        private long windowStart;
        private int count;

        private WindowCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

