# Token Monitoring & Context Optimization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 给 JaguarClaw 加上 token 监控（实时上报到前端）+ 滑动窗口（激活已有配置）+ P2 智能压缩扩展插槽。

**Architecture:** 分四层落地：数据模型扩展 → streaming 路径 usage 采集 → ContextBuilder 窗口限制 → 前端 token 状态栏。所有变更都是增量式的，不破坏现有 API 兼容性。P2 智能压缩通过 `ContextCompactionService` 接口留好插槽，默认空实现，后续替换不影响现有代码。

**Tech Stack:** Java 17 + Spring Boot 3 + WebFlux / Vue 3 + TypeScript / WebSocket RPC

---

## 关键文件速查

| 文件 | 路径 |
|------|------|
| LlmResponse | `src/main/java/com/jaguarliu/ai/llm/model/LlmResponse.java` |
| LlmChunk | `src/main/java/com/jaguarliu/ai/llm/model/LlmChunk.java` |
| LlmRequest | `src/main/java/com/jaguarliu/ai/llm/model/LlmRequest.java` |
| OpenAiCompatibleLlmClient | `src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java` |
| RunContext | `src/main/java/com/jaguarliu/ai/runtime/RunContext.java` |
| AgentRuntime | `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java` |
| ContextBuilder | `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java` |
| AgentEvent | `src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java` |
| application.yml | `src/main/resources/application.yml` |
| useChat.ts | `jaguarclaw-ui/src/composables/useChat.ts` |
| MessageInput.vue | `jaguarclaw-ui/src/components/MessageInput.vue` |
| WorkspaceView.vue | `jaguarclaw-ui/src/views/WorkspaceView.vue` |

---

### Task 1: 扩展 LlmResponse.Usage — 加入缓存 token 字段

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmResponse.java:50-54`

**Step 1: 在 Usage 内部类中添加缓存字段**

在 `LlmResponse.Usage` 的现有三个字段后面追加：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public static class Usage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    // 新增：provider 侧缓存字段（Claude 等支持时自动填充，其他 provider 为 null）
    private Integer cacheReadInputTokens;
    private Integer cacheCreationInputTokens;
}
```

**Step 2: 验证编译通过**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS，无编译报错

**Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/llm/model/LlmResponse.java
git commit -m "feat(llm): add cache token fields to LlmResponse.Usage"
```

---

### Task 2: 扩展 LlmChunk — 加入 usage 字段

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmChunk.java`

**Step 1: 在 LlmChunk 末尾添加 usage 字段**

在 `toolCallArgumentsDelta` 字段之后追加：

```java
/**
 * token 用量（仅 streaming 最后一个 chunk 携带，其他 chunk 为 null）
 */
private LlmResponse.Usage usage;
```

同时在文件顶部添加 import（如果不在同一包下）：

```java
import com.jaguarliu.ai.llm.model.LlmResponse;
```

**Step 2: 验证编译**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/llm/model/LlmChunk.java
git commit -m "feat(llm): add usage field to LlmChunk for streaming usage capture"
```

---

### Task 3: RunContext 加入 token 累加器

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/RunContext.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/RunContextTest.java`

**Step 1: 写失败测试**

在 `RunContextTest.java` 中添加：

```java
@Test
void addUsage_accumulatesAcrossMultipleCalls() {
    RunContext ctx = RunContext.create("r1", "c1", "s1",
        LoopConfig.defaults(), mock(CancellationManager.class));

    LlmResponse.Usage u1 = LlmResponse.Usage.builder()
        .promptTokens(100).completionTokens(50).totalTokens(150)
        .cacheReadInputTokens(80).cacheCreationInputTokens(0).build();
    LlmResponse.Usage u2 = LlmResponse.Usage.builder()
        .promptTokens(200).completionTokens(100).totalTokens(300)
        .cacheReadInputTokens(160).cacheCreationInputTokens(0).build();

    ctx.addUsage(u1);
    ctx.addUsage(u2);

    assertThat(ctx.getTotalInputTokens()).isEqualTo(300);
    assertThat(ctx.getTotalOutputTokens()).isEqualTo(150);
    assertThat(ctx.getTotalCacheReadTokens()).isEqualTo(240);
    assertThat(ctx.getTotalTokens()).isEqualTo(450);
}

@Test
void addUsage_handlesNullFields_gracefully() {
    RunContext ctx = RunContext.create("r1", "c1", "s1",
        LoopConfig.defaults(), mock(CancellationManager.class));

    // provider 不支持缓存时，cache 字段为 null
    LlmResponse.Usage u = LlmResponse.Usage.builder()
        .promptTokens(100).completionTokens(50).totalTokens(150)
        .build(); // cacheReadInputTokens = null

    assertThatCode(() -> ctx.addUsage(u)).doesNotThrowAnyException();
    assertThat(ctx.getTotalInputTokens()).isEqualTo(100);
}
```

**Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl . -Dtest=RunContextTest -q
```
Expected: FAIL — `addUsage`, `getTotalInputTokens` 等方法不存在

**Step 3: 在 RunContext 中实现累加器**

在 `RunContext.java` 的 `currentStep` 字段之后，在 `originalInput` 字段之前，添加：

```java
/**
 * 整个 run 累计 token 消耗（线程安全）
 */
@Builder.Default
private final AtomicInteger totalInputTokens = new AtomicInteger(0);

@Builder.Default
private final AtomicInteger totalOutputTokens = new AtomicInteger(0);

@Builder.Default
private final AtomicInteger totalCacheReadTokens = new AtomicInteger(0);

/**
 * 累加单次 LLM 调用的 usage（null-safe）
 */
public void addUsage(LlmResponse.Usage usage) {
    if (usage == null) return;
    if (usage.getPromptTokens() != null) {
        totalInputTokens.addAndGet(usage.getPromptTokens());
    }
    if (usage.getCompletionTokens() != null) {
        totalOutputTokens.addAndGet(usage.getCompletionTokens());
    }
    if (usage.getCacheReadInputTokens() != null) {
        totalCacheReadTokens.addAndGet(usage.getCacheReadInputTokens());
    }
}

public int getTotalInputTokens()    { return totalInputTokens.get(); }
public int getTotalOutputTokens()   { return totalOutputTokens.get(); }
public int getTotalCacheReadTokens(){ return totalCacheReadTokens.get(); }
public int getTotalTokens()         { return totalInputTokens.get() + totalOutputTokens.get(); }
```

在文件顶部确认 import 存在：
```java
import com.jaguarliu.ai.llm.model.LlmResponse;
```

**Step 4: 运行测试，确认通过**

```bash
./mvnw test -pl . -Dtest=RunContextTest -q
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/RunContext.java \
        src/test/java/com/jaguarliu/ai/runtime/RunContextTest.java
git commit -m "feat(runtime): add token usage accumulators to RunContext"
```

---

### Task 4: AgentEvent 加入 TOKEN_USAGE 事件

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java`

**Step 1: 在 EventType 枚举中新增两个类型**

在 `HEARTBEAT_NOTIFY` 之后添加：

```java
// Token 监控事件
TOKEN_USAGE("token.usage"),
// P2 扩展占位：上下文压缩通知
CONTEXT_COMPACTED("context.compacted");
```

**Step 2: 新增 TokenUsageData 内部类**

在文件末尾的 `HeartbeatNotifyData` 之后添加：

```java
@Data
@AllArgsConstructor
public static class TokenUsageData {
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private int cacheReadTokens;   // 0 表示不支持或未命中
    private int historyMessages;   // 当前上下文历史消息条数
    private int step;              // 当前循环步数
}
```

**Step 3: 新增工厂方法**

在 `heartbeatNotify` 方法之后添加：

```java
/**
 * 创建 token.usage 事件（每次 LLM 调用后发布）
 */
public static AgentEvent tokenUsage(String connectionId, String runId,
                                     LlmResponse.Usage usage,
                                     int historyMessages, int step) {
    int cacheRead = usage.getCacheReadInputTokens() != null
        ? usage.getCacheReadInputTokens() : 0;
    return AgentEvent.builder()
        .type(EventType.TOKEN_USAGE)
        .connectionId(connectionId)
        .runId(runId)
        .data(new TokenUsageData(
            usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
            usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0,
            usage.getTotalTokens() != null ? usage.getTotalTokens() : 0,
            cacheRead,
            historyMessages,
            step
        ))
        .build();
}
```

在文件顶部添加 import：
```java
import com.jaguarliu.ai.llm.model.LlmResponse;
```

**Step 4: 验证编译**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/events/AgentEvent.java
git commit -m "feat(events): add TOKEN_USAGE and CONTEXT_COMPACTED event types"
```

---

### Task 5: OpenAiCompatibleLlmClient — streaming 路径采集 usage

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java:245-324`

**背景：** 当前 `parseSseChunk()` 只解析 `choices` 节点，忽略了最后一帧里的 `usage` 节点（OpenAI 和 Claude 在 streaming 结束时都会在同一帧里返回 usage）。

**Step 1: 在 `parseSseChunk()` 方法的 `try` 块里，解析完 choices 之后追加 usage 解析逻辑**

找到这段代码（约 255-320 行）：
```java
// 如果没有内容、没有 tool_calls、不是结束，跳过
if (content == null && !isDone && accumulators.isEmpty()) {
    return Flux.empty();
}

return Flux.just(chunkBuilder.build());
```

在 `return Flux.just(chunkBuilder.build())` 之前插入：

```java
// 解析 usage（streaming 最后一帧携带，OpenAI 和 Claude 格式一致）
JsonNode usageNode = root.get("usage");
if (usageNode != null && !usageNode.isNull()) {
    LlmResponse.Usage usage = LlmResponse.Usage.builder()
        .promptTokens(usageNode.path("prompt_tokens").asInt(0))
        .completionTokens(usageNode.path("completion_tokens").asInt(0))
        .totalTokens(usageNode.path("total_tokens").asInt(0))
        // Claude 扩展字段（有则读，无则 null）
        .cacheReadInputTokens(usageNode.has("cache_read_input_tokens")
            ? usageNode.get("cache_read_input_tokens").asInt() : null)
        .cacheCreationInputTokens(usageNode.has("cache_creation_input_tokens")
            ? usageNode.get("cache_creation_input_tokens").asInt() : null)
        .build();
    chunkBuilder.usage(usage);
}
```

在文件顶部确认 import 存在（通常已有）：
```java
import com.jaguarliu.ai.llm.model.LlmResponse;
```

**Step 2: 验证编译**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java
git commit -m "feat(llm): capture usage from streaming final chunk in parseSseChunk"
```

---

### Task 6: AgentRuntime — usage 汇入 RunContext 并发布事件

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java:360-387`

**背景：** `streamLlmCall` 目前只收集 `delta` 和 `toolCalls`，拿到 `usage` 后没有任何处理。需要：
1. 把 `RunContext` 传进 `streamLlmCall`（现在只传了 `connectionId` 和 `runId`）
2. 收集最后一帧的 `usage`
3. 调 `context.addUsage()`
4. 发布 `token.usage` 事件

**Step 1: 修改 `streamLlmCall` 签名，接收 RunContext**

将：
```java
private StepResult streamLlmCall(String connectionId, String runId, LlmRequest request) {
```
改为：
```java
private StepResult streamLlmCall(RunContext context, LlmRequest request) {
    String connectionId = context.getConnectionId();
    String runId = context.getRunId();
```

**Step 2: 在 streamLlmCall 的 blockLast() 前，添加 usage 采集逻辑**

将：
```java
llmClient.stream(request)
        .doOnNext(chunk -> {
            if (chunk.getDelta() != null) {
                content.append(chunk.getDelta());
                eventBus.publish(AgentEvent.assistantDelta(connectionId, runId, chunk.getDelta()));
                // ...artifact 相关...
            }
            if (chunk.getToolCalls() != null) {
                toolCalls.addAll(chunk.getToolCalls());
            }
        })
        .blockLast();
```

改为（添加 usage 采集 + 使用数组持有 usage 引用，因为 lambda 要求 effectively final）：

```java
LlmResponse.Usage[] usageHolder = {null};

llmClient.stream(request)
        .doOnNext(chunk -> {
            if (chunk.getDelta() != null) {
                content.append(chunk.getDelta());
                eventBus.publish(AgentEvent.assistantDelta(connectionId, runId, chunk.getDelta()));

                ArtifactStreamExtractor.ExtractionResult result = artifactExtractor.append(chunk.getDelta());
                if (result.pathDetected() != null) {
                    eventBus.publish(AgentEvent.artifactOpen(connectionId, runId, result.pathDetected()));
                }
                if (result.contentDelta() != null) {
                    eventBus.publish(AgentEvent.artifactDelta(connectionId, runId, result.contentDelta()));
                }
            }
            if (chunk.getToolCalls() != null) {
                toolCalls.addAll(chunk.getToolCalls());
            }
            // 采集 usage（仅最后一帧有值）
            if (chunk.getUsage() != null) {
                usageHolder[0] = chunk.getUsage();
            }
        })
        .blockLast();

// 汇入 RunContext 并发布事件
if (usageHolder[0] != null) {
    context.addUsage(usageHolder[0]);
    // historyMessages：从 request.messages 里数，去掉 system 消息和最后一条 user 消息
    int historyCount = (int) request.getMessages().stream()
        .filter(m -> !"system".equals(m.getRole()))
        .count() - 1; // 减去当前 user 消息
    historyCount = Math.max(0, historyCount);
    eventBus.publish(AgentEvent.tokenUsage(
        connectionId, runId, usageHolder[0], historyCount, context.getCurrentStep()));
    log.debug("Token usage: step={}, input={}, output={}, cacheRead={}",
        context.getCurrentStep(),
        usageHolder[0].getPromptTokens(),
        usageHolder[0].getCompletionTokens(),
        usageHolder[0].getCacheReadInputTokens());
}
```

**Step 3: 修复调用处**

找到 `streamLlmCall` 的调用处（约 333 行）：
```java
return streamLlmCall(context.getConnectionId(), context.getRunId(), requestBuilder.build());
```
改为：
```java
return streamLlmCall(context, requestBuilder.build());
```

**Step 4: 验证编译**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS

**Step 5: 跑现有测试，确保没有回归**

```bash
./mvnw test -pl . -q
```
Expected: 全部通过（或仅有已知失败）

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java
git commit -m "feat(runtime): collect and publish token usage after each LLM streaming call"
```

---

### Task 7: ContextBuilder 滑动窗口 — 激活已有配置

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/ContextBuilderTest.java`

**背景：** `application.yml` 里已有 `agent.max-history-messages: 20`，但 `ContextBuilder` 完全没有注入和使用这个值。核心方法是 `buildMessages()`，所有 `build*` 系列方法最终都调 `build(systemPrompt, history, userPrompt)`，窗口截断只需在历史消息那一步加一行。

**Step 1: 写失败测试**

在 `ContextBuilderTest.java` 中添加：

```java
@Test
void buildMessages_appliesSlidingWindow_whenHistoryExceedsLimit() {
    // 构造 30 条历史消息（user/assistant 交替）
    List<LlmRequest.Message> longHistory = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
        longHistory.add(LlmRequest.Message.user("question " + i));
        longHistory.add(LlmRequest.Message.assistant("answer " + i));
    }

    // maxHistoryMessages = 10（通过反射或构造时设置）
    // 调用 build()
    LlmRequest request = contextBuilder.build("system", longHistory, "new question");

    // 期望：messages = 1(system) + 10(windowed) + 1(user) = 12
    long nonSystemCount = request.getMessages().stream()
        .filter(m -> !"system".equals(m.getRole()) && !"user".equals(m.getRole())
            || isLastUserMessage(m, request.getMessages()))
        .count();
    // 历史消息应该被截断为 maxHistoryMessages 条
    long historyCount = request.getMessages().stream()
        .filter(m -> !"system".equals(m.getRole()))
        .count() - 1; // 减去最后的 user 消息
    assertThat(historyCount).isLessThanOrEqualTo(10);
}

@Test
void buildMessages_preservesToolCallPairs_whenTruncating() {
    // 历史消息里有 tool_call / tool_result 配对
    List<LlmRequest.Message> history = new ArrayList<>();
    // 填满到临界点，最后几条是 tool_call 配对
    for (int i = 0; i < 9; i++) {
        history.add(LlmRequest.Message.user("q" + i));
        history.add(LlmRequest.Message.assistant("a" + i));
    }
    // 第 19-20 条是 tool_call 配对（不应该被截断成只有 tool_result 没有 assistant）
    ToolCall tc = ToolCall.builder().id("tc1").type("function")
        .function(ToolCall.FunctionCall.builder().name("ping").arguments("{}").build()).build();
    history.add(LlmRequest.Message.assistantWithToolCalls(List.of(tc)));
    history.add(LlmRequest.Message.toolResult("tc1", "pong"));

    LlmRequest request = contextBuilder.build("system", history, "new question");

    // 确认没有孤立的 tool result（找到 tool 消息则前一条必须是带 tool_calls 的 assistant）
    List<LlmRequest.Message> msgs = request.getMessages();
    for (int i = 0; i < msgs.size(); i++) {
        if ("tool".equals(msgs.get(i).getRole())) {
            assertThat(i).isGreaterThan(0);
            assertThat(msgs.get(i - 1).getToolCalls()).isNotEmpty();
        }
    }
}
```

**Step 2: 运行测试，确认失败**

```bash
./mvnw test -pl . -Dtest=ContextBuilderTest -q
```
Expected: FAIL

**Step 3: 在 ContextBuilder 中注入配置并实现滑动窗口**

在 `autoSelectEnabled` 字段之后添加：

```java
@Value("${agent.max-history-messages:20}")
private int maxHistoryMessages;
```

实现私有辅助方法（在类末尾，`SkillAwareRequest` record 之前）：

```java
/**
 * 滑动窗口截断历史消息，保留最近 maxHistoryMessages 条。
 * 确保从 user/assistant 轮次边界截断，避免孤立的 tool_result 消息。
 */
private List<LlmRequest.Message> applyWindow(List<LlmRequest.Message> history) {
    if (history == null || history.size() <= maxHistoryMessages) {
        return history;
    }
    // 从末尾取 maxHistoryMessages 条
    List<LlmRequest.Message> windowed = history.subList(
        history.size() - maxHistoryMessages, history.size()
    );
    // 如果第一条是 tool（孤立 tool_result），向后再移一位直到非 tool 开头
    int start = 0;
    while (start < windowed.size() && "tool".equals(windowed.get(start).getRole())) {
        start++;
    }
    return start == 0 ? windowed : windowed.subList(start, windowed.size());
}
```

在 `build(String systemPrompt, List<LlmRequest.Message> history, String userPrompt)` 方法的历史消息添加处，将：

```java
// 2. 历史消息
if (history != null && !history.isEmpty()) {
    messages.addAll(history);
}
```

改为：

```java
// 2. 历史消息（滑动窗口）
if (history != null && !history.isEmpty()) {
    messages.addAll(applyWindow(history));
}
```

同样在 `buildMessages(...)` 方法的历史消息处做相同替换：

```java
// 2. 历史消息（滑动窗口）
if (history != null && !history.isEmpty()) {
    messages.addAll(applyWindow(history));
}
```

**Step 4: 运行测试，确认通过**

```bash
./mvnw test -pl . -Dtest=ContextBuilderTest -q
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java \
        src/test/java/com/jaguarliu/ai/runtime/ContextBuilderTest.java
git commit -m "feat(context): activate sliding window using existing max-history-messages config"
```

---

### Task 8: ContextCompactionService 扩展插槽 (P2 预留)

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/ContextCompactionService.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/NoOpContextCompactionService.java`

**目的：** 定义接口 + 空实现，让 ContextBuilder 依赖接口。P2 实现时只需新建实现类，标 `@Primary`，其他代码零改动。

**Step 1: 创建接口**

```java
// ContextCompactionService.java
package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import java.util.List;

/**
 * 上下文压缩服务接口
 * P1: NoOpContextCompactionService（空实现，直接返回）
 * P2: LlmContextCompactionService（用 LLM 生成摘要压缩早期对话）
 */
public interface ContextCompactionService {

    /**
     * 判断是否需要压缩
     * @param history 当前历史消息
     * @param estimatedTokens 估算的 token 数
     */
    boolean shouldCompact(List<LlmRequest.Message> history, int estimatedTokens);

    /**
     * 执行压缩，返回压缩后的历史消息
     */
    CompactionResult compact(List<LlmRequest.Message> history);

    record CompactionResult(List<LlmRequest.Message> messages, String summary, boolean compacted) {
        static CompactionResult noOp(List<LlmRequest.Message> messages) {
            return new CompactionResult(messages, null, false);
        }
    }
}
```

**Step 2: 创建默认空实现**

```java
// NoOpContextCompactionService.java
package com.jaguarliu.ai.runtime;

import com.jaguarliu.ai.llm.model.LlmRequest;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 上下文压缩默认空实现
 * P1 使用此实现（不压缩）。
 * P2 实现时创建新类并标 @Primary 覆盖此 Bean。
 */
@Component
public class NoOpContextCompactionService implements ContextCompactionService {

    @Override
    public boolean shouldCompact(List<LlmRequest.Message> history, int estimatedTokens) {
        return false; // P1 永不压缩
    }

    @Override
    public CompactionResult compact(List<LlmRequest.Message> history) {
        return CompactionResult.noOp(history);
    }
}
```

**Step 3: 在 ContextBuilder 注入接口并预留调用点**

在 `ContextBuilder` 构造函数参数和字段中添加：

```java
private final ContextCompactionService compactionService;

public ContextBuilder(
        ToolRegistry toolRegistry,
        SkillRegistry skillRegistry,
        SkillIndexBuilder skillIndexBuilder,
        SkillSelector skillSelector,
        SkillTemplateEngine templateEngine,
        SystemPromptBuilder systemPromptBuilder,
        ContextCompactionService compactionService) {  // 新增
    // ... 现有赋值 ...
    this.compactionService = compactionService;
}
```

在 `applyWindow()` 之前调用（在 `build()` 方法的历史消息处），P1 下 shouldCompact 永远返回 false，此处代码完全无副作用：

```java
// 2. 历史消息（P2 压缩插槽 → 滑动窗口）
if (history != null && !history.isEmpty()) {
    List<LlmRequest.Message> processedHistory = history;
    // P2 压缩检查（P1 NoOp 实现永远返回 false）
    if (compactionService.shouldCompact(history, estimateTokens(history))) {
        processedHistory = compactionService.compact(history).messages();
        log.info("Context compacted: {} -> {} messages", history.size(), processedHistory.size());
    }
    messages.addAll(applyWindow(processedHistory));
}
```

同时添加 token 估算辅助方法（粗略估算，无需 tokenizer）：

```java
/** 粗略估算 token 数（1 token ≈ 4 chars，仅用于压缩阈值判断） */
private int estimateTokens(List<LlmRequest.Message> messages) {
    return messages.stream()
        .mapToInt(m -> m.getContent() != null ? m.getContent().length() / 4 : 10)
        .sum();
}
```

**Step 4: 更新 application.yml，新增 P2 配置项（注释掉，供 P2 时启用）**

在 `agent` 段落的 `max-history-messages` 下添加：

```yaml
agent:
  max-history-messages: 20      # 滑动窗口：保留最近 N 条消息
  # P2 智能压缩配置（当前不生效，NoOpContextCompactionService 下 shouldCompact 永远 false）
  # context-window-limit: 80000  # 估算 token 超过此值时触发压缩
  # compaction-keep-recent: 10   # 压缩时保留最近 N 条完整消息
```

**Step 5: 验证编译和测试**

```bash
./mvnw test -pl . -q
```
Expected: 全部通过

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/ContextCompactionService.java \
        src/main/java/com/jaguarliu/ai/runtime/NoOpContextCompactionService.java \
        src/main/java/com/jaguarliu/ai/runtime/ContextBuilder.java \
        src/main/resources/application.yml
git commit -m "feat(context): add ContextCompactionService interface and NoOp stub for P2 smart compaction"
```

---

### Task 9: 前端 useChat.ts — 接收 token.usage 事件

**Files:**
- Modify: `jaguarclaw-ui/src/composables/useChat.ts`

**Step 1: 在文件顶部（interfaces 区域）添加 TokenUsage 类型**

找到现有的 payload 类型定义区域，添加：

```typescript
interface TokenUsagePayload {
  inputTokens: number
  outputTokens: number
  totalTokens: number
  cacheReadTokens: number
  historyMessages: number
  step: number
}
```

**Step 2: 添加 currentRunUsage 响应式状态**

在 `streamBlocks` 等响应式状态附近添加：

```typescript
// Token 监控：当前 run 最新一次 LLM 调用的 usage
const currentRunUsage = ref<TokenUsagePayload | null>(null)
```

**Step 3: 在事件注册区域添加 token.usage 处理器**

在 `onEvent('heartbeat.notify', ...)` 之后，在 `onEvent('lifecycle.end', ...)` 之前添加：

```typescript
eventCleanups.push(
  onEvent('token.usage', (event: RpcEvent) => {
    if (currentRun.value && event.runId === currentRun.value.id) {
      currentRunUsage.value = event.payload as TokenUsagePayload
    }
  }),
)
```

**Step 4: 在 lifecycle.end 和 lifecycle.error 处理器里重置 usage**

在 `lifecycle.end` 的处理器末尾（currentRun.value = null 之前）：
```typescript
currentRunUsage.value = null
```

在 `lifecycle.error` 的处理器里同样添加：
```typescript
currentRunUsage.value = null
```

**Step 5: 在 return 对象中暴露 currentRunUsage**

找到 `return {` 语句，在已有暴露项中添加：
```typescript
currentRunUsage,
```

**Step 6: 验证前端编译**

```bash
cd jaguarclaw-ui && npm run type-check
```
Expected: 无类型错误

**Step 7: Commit**

```bash
git add jaguarclaw-ui/src/composables/useChat.ts
git commit -m "feat(ui): handle token.usage event in useChat composable"
```

---

### Task 10: 前端 MessageInput.vue — token 状态栏

**Files:**
- Modify: `jaguarclaw-ui/src/components/MessageInput.vue`

**Step 1: 在 props 定义中新增 tokenUsage prop**

找到现有的 `defineProps`，在其中添加：

```typescript
const props = defineProps<{
  // ...现有 props...
  tokenUsage?: {
    inputTokens: number
    outputTokens: number
    totalTokens: number
    cacheReadTokens: number
    historyMessages: number
    step: number
  } | null
  isRunning?: boolean  // 如果还没有此 prop，添加；已有则跳过
}>()
```

**Step 2: 添加 token 格式化辅助函数**

在 `<script setup>` 中添加：

```typescript
function formatTokens(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}
```

**Step 3: 在模板里加状态栏**

在输入框的容器末尾（提交按钮之后，`</div>` 关闭之前）添加：

```html
<!-- Token 状态栏：仅在 run 进行中且有 usage 数据时显示 -->
<div
  v-if="isRunning && tokenUsage"
  class="token-status-bar"
>
  <span>Step {{ tokenUsage.step }}</span>
  <span class="separator">·</span>
  <span>{{ formatTokens(tokenUsage.totalTokens) }} tokens</span>
  <span class="separator">·</span>
  <span>{{ tokenUsage.historyMessages }} 条上下文</span>
  <span v-if="tokenUsage.cacheReadTokens > 0" class="separator">·</span>
  <span v-if="tokenUsage.cacheReadTokens > 0" class="cache-hit">
    ↩ {{ formatTokens(tokenUsage.cacheReadTokens) }} cached
  </span>
</div>
```

**Step 4: 添加样式**

在 `<style scoped>` 中添加：

```css
.token-status-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  font-size: 11px;
  color: var(--color-gray-500);
  font-family: var(--font-mono);
}

.token-status-bar .separator {
  color: var(--color-gray-300);
}

.token-status-bar .cache-hit {
  color: var(--color-gray-400);
}
```

**Step 5: 验证编译**

```bash
cd jaguarclaw-ui && npm run type-check
```
Expected: 无类型错误

**Step 6: Commit**

```bash
git add jaguarclaw-ui/src/components/MessageInput.vue
git commit -m "feat(ui): add token status bar to MessageInput"
```

---

### Task 11: WorkspaceView.vue — 连通数据流

**Files:**
- Modify: `jaguarclaw-ui/src/views/WorkspaceView.vue`

**Step 1: 从 useChat 解构 currentRunUsage**

找到 `useChat()` 的解构赋值，添加 `currentRunUsage`：

```typescript
const {
  // ...现有解构...
  currentRunUsage,
} = useChat()
```

**Step 2: 将 tokenUsage 和 isRunning 传给 MessageInput**

找到 `<MessageInput` 组件，添加两个 prop：

```html
<MessageInput
  ...现有 props...
  :token-usage="currentRunUsage"
  :is-running="!!currentRun"
/>
```

**Step 3: 验证前端完整构建**

```bash
cd jaguarclaw-ui && npm run build
```
Expected: BUILD 成功，无 TypeScript 错误

**Step 4: Commit**

```bash
git add jaguarclaw-ui/src/views/WorkspaceView.vue
git commit -m "feat(ui): wire token usage from useChat to MessageInput"
```

---

## 验收检查清单

运行完所有 Task 后，验证以下行为：

1. **Token 采集**：发起一次对话，查看后端日志，应该有 `Token usage: step=1, input=xxx, output=xxx` 输出
2. **前端展示**：对话进行中，MessageInput 底部出现状态栏（`Step 1 · 1.2k tokens · 0 条上下文`）
3. **滑动窗口**：发起超过 20 轮的对话，第 21 轮起历史消息不超过 20 条（可通过日志 `Built context: history=20 msgs` 确认）
4. **P2 插槽**：编写一个 `LlmContextCompactionService implements ContextCompactionService` 并标 `@Primary`，启动后应该接管压缩逻辑，无需改动其他文件
5. **Null safety**：使用不支持 usage 的 provider（如 Ollama）时，不报错，状态栏不显示

---

## 已知边界

- **streaming usage 有无**：部分 Ollama 模型不在流式响应里返回 usage，此时 `usageHolder[0]` 为 null，事件不发布，前端状态栏不显示，属于正常行为
- **tool_call pair 保护**：`applyWindow` 的孤立 tool 检测只处理开头，若截断点在 tool_call 中间（assistant 有 tool_calls 但 tool_result 被截掉），需要额外向前扩展；当前实现为简单的"跳过开头的 tool 消息"，生产中 `max-history-messages` 建议设为偶数以减少截断到配对中间的概率
- **token 估算精度**：`estimateTokens()` 用 `content.length / 4` 粗估，误差约 ±20%；P2 可替换为实际 tokenizer（如 jtokkit）
