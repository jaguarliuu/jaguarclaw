package com.jaguarliu.ai.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "agent.loop")
public class LoopConfig {

    /**
     * 最大循环步数（防止无限循环）
     */
    @Builder.Default
    private int maxSteps = 10;

    /**
     * 整个循环的超时时间（秒）
     */
    @Builder.Default
    private long runTimeoutSeconds = 300;

    /**
     * 单步超时时间（秒），包含 LLM 调用 + 工具执行
     */
    @Builder.Default
    private long stepTimeoutSeconds = 120;

    /**
     * 整个 run 的最大 token 预算，0 表示不限制。
     */
    @Builder.Default
    private int maxTokens = 0;

    /**
     * 整个 run 的最大美元成本预算，0 表示不限制。
     */
    @Builder.Default
    private double maxCostUsd = 0;

    /**
     * 创建一个覆盖了 maxSteps 的新配置实例，其余参数继承自 base
     */
    public static LoopConfig withMaxSteps(int maxSteps, LoopConfig base) {
        LoopConfig config = new LoopConfig();
        config.setMaxSteps(maxSteps);
        config.setRunTimeoutSeconds(base.getRunTimeoutSeconds());
        config.setStepTimeoutSeconds(base.getStepTimeoutSeconds());
        config.setMaxTokens(base.getMaxTokens());
        config.setMaxCostUsd(base.getMaxCostUsd());
        return config;
    }
}
