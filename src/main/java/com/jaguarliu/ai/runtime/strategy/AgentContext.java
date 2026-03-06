package com.jaguarliu.ai.runtime.strategy;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * Agent 策略决策的输入上下文
 * 包含路由到不同策略所需的全部信息
 */
@Getter
@Builder
public class AgentContext {

    private final String sessionId;
    private final String runId;
    private final String connectionId;
    private final String agentId;
    private final String prompt;

    /**
     * 排除的 MCP 服务器名称集合
     */
    private final Set<String> excludedMcpServers;

    // 未来扩展: documentId, legalCaseId 等
}
