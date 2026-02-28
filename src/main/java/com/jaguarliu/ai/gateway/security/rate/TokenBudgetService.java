package com.jaguarliu.ai.gateway.security.rate;

import com.jaguarliu.ai.gateway.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每主体每日 token 预算控制（内存版）
 */
@Component
@RequiredArgsConstructor
public class TokenBudgetService {

    private final SecurityProperties securityProperties;
    private final Map<String, DailyBudget> budgets = new ConcurrentHashMap<>();

    public boolean tryConsume(String principalId, int tokens) {
        if (principalId == null || principalId.isBlank()) {
            return false;
        }

        int safeTokens = Math.max(tokens, 0);
        int dailyLimit = securityProperties.getRateLimit().getTokenPerDayPerPrincipal();
        DailyBudget budget = budgets.computeIfAbsent(principalId, ignored -> new DailyBudget(LocalDate.now(), 0));

        synchronized (budget) {
            LocalDate today = LocalDate.now();
            if (!today.equals(budget.date)) {
                budget.date = today;
                budget.used = 0;
            }

            if (budget.used + safeTokens > dailyLimit) {
                return false;
            }
            budget.used += safeTokens;
            return true;
        }
    }

    public int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 1;
        }
        return Math.max(1, content.length() / 4);
    }

    private static class DailyBudget {
        private LocalDate date;
        private int used;

        private DailyBudget(LocalDate date, int used) {
            this.date = date;
            this.used = used;
        }
    }
}

