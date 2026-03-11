package com.jaguarliu.ai.tools.builtin.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.events.AgentEvent;
import com.jaguarliu.ai.gateway.events.EventBus;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 在当前文档中插入 ECharts 图表节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DrawChartTool implements Tool {

    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("draw_chart")
                .description("在当前打开的文档中插入一个数据图表（柱状图、折线图、饼图、散点图等）。" +
                        "图表会实时渲染并显示在用户的文档编辑器中。" +
                        "spec 参数为完整的 ECharts option 对象（JSON 字符串），包含 title、series、xAxis、yAxis、legend 等配置。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "doc_id", Map.of(
                                        "type", "string",
                                        "description", "当前文档的 ID"
                                ),
                                "spec", Map.of(
                                        "type", "string",
                                        "description", "ECharts option 对象的 JSON 字符串，例如：" +
                                                "{\"title\":{\"text\":\"销售数据\"}," +
                                                "\"xAxis\":{\"data\":[\"Q1\",\"Q2\",\"Q3\"]}," +
                                                "\"yAxis\":{}," +
                                                "\"series\":[{\"type\":\"bar\",\"data\":[120,200,150]}]}"
                                )
                        ),
                        "required", List.of("doc_id", "spec")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String docId = (String) arguments.get("doc_id");
            String spec = (String) arguments.get("spec");

            if (docId == null || docId.isBlank()) {
                return ToolResult.error("doc_id is required");
            }
            if (spec == null || spec.isBlank()) {
                return ToolResult.error("spec is required");
            }

            // Validate that spec is valid JSON
            try {
                objectMapper.readTree(spec);
            } catch (Exception e) {
                return ToolResult.error("spec 不是有效的 JSON：" + e.getMessage());
            }

            ToolExecutionContext ctx = ToolExecutionContext.current();
            String connectionId = ctx != null ? ctx.getConnectionId() : null;
            String runId = ctx != null ? ctx.getRunId() : null;

            if (connectionId == null || runId == null) {
                log.warn("draw_chart: connectionId or runId is null, cannot push node to editor");
                return ToolResult.error("无法推送图表：WebSocket 连接不可用");
            }

            // Build TipTap chartBlock node — spec is stored as a JSON string attribute
            Map<String, Object> node = Map.of(
                    "type", "chartBlock",
                    "attrs", Map.of("spec", spec)
            );

            eventBus.publish(AgentEvent.docNodeInsert(connectionId, runId, docId, node));
            log.info("draw_chart: pushed chart node to doc={} connection={}", docId, connectionId);

            return ToolResult.success("图表已插入文档");
        });
    }
}
