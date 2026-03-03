# LLM 会话上下文管理优化方案

> 问题：当前系统累计全量历史会话，导致上下文爆炸
> 目标：优化 token 消耗，提升性能，保持上下文连续性
> 日期：2026-03-02

---

## 🔍 现状分析

### 当前实现

```java
// ContextBuilder.java - 当前实现
public LlmRequest build(String systemPrompt, List<LlmRequest.Message> history, String userPrompt) {
    List<LlmRequest.Message> messages = new ArrayList<>();
    
    // 1. System prompt（固定）
    messages.add(LlmRequest.Message.system(system));
    
    // 2. 历史消息（全量追加）
    if (history != null && !history.isEmpty()) {
        messages.addAll(history);  // ❌ 问题：全量追加
    }
    
    // 3. 当前用户输入
    messages.add(LlmRequest.Message.user(userPrompt));
    
    return LlmRequest.builder().messages(messages).build();
}
```

### 问题分析

| 问题 | 影响 | 严重性 |
|------|------|--------|
| **全量历史** | Token 线性增长 | 高 |
| **无压缩** | 成本指数增长 | 高 |
| **无选择** | 关键信息淹没 | 中 |
| **无统计** | 无法监控消耗 | 中 |

**场景示例**：

```
第 1 轮：1,000 tokens (system + 1 question)
第 5 轮：3,000 tokens (system + 5 questions + 5 answers)
第 10 轮：6,000 tokens
第 20 轮：12,000 tokens
第 50 轮：30,000 tokens  ← 超过大多数模型限制
```

---

## 📚 业界最佳实践调研

### 0. Prompt Caching / Prefix Caching（最重要！）

**原理**：
- 缓存静态前缀（System Prompt + Tools + 历史对话）
- 后续请求复用缓存，只处理新增部分
- **成本降低 90%**（缓存读取价格是正常的 10%）

**工作方式**：
```
Request 1: [System + Tools + History + User Message]
            └─────── 缓存写入 ───────┘
            
Request 2: [System + Tools + History + User Message + Asst + User]
            └──── 缓存读取 ────┘  └─ 新内容 ─┘
```

**缓存层级**：
```
tools → system → messages
(按顺序缓存，修改任何一层会失效后面所有)
```

**价格对比**（Claude Opus 4.6）：
| 类型 | 价格/MTok | 相当于 |
|------|-----------|--------|
| Base Input | $5 | 100% |
| Cache Write (5m) | $6.25 | 125% |
| Cache Write (1h) | $10 | 200% |
| **Cache Read** | **$0.50** | **10%** |

**最小缓存大小**：
- Claude Opus 4.6: 4096 tokens
- Claude Sonnet 4.6: 2048 tokens
- Claude Haiku 3: 2048 tokens

**Java 实现示例**：
```java
// 方式 1：自动缓存（推荐）
MessageCreateParams params = MessageCreateParams.builder()
    .model(Model.CLAUDE_OPUS_4_6)
    .maxTokens(1024)
    .cacheControl(CacheControlEphemeral.builder().build()) // 自动缓存到最后一个 block
    .system("You are a helpful assistant...")
    .messages(messages)
    .build();

// 方式 2：显式缓存（精细控制）
MessageCreateParams params = MessageCreateParams.builder()
    .model(Model.CLAUDE_OPUS_4_6)
    .system(List.of(
        TextBlockParam.builder()
            .text("Static system instructions...")
            .cacheControl(CacheControlEphemeral.builder().build()) // 缓存点 1
            .build()
    ))
    .tools(List.of(
        Tool.builder()
            .name("get_weather")
            // ...
            .cacheControl(CacheControlEphemeral.builder().build()) // 缓存点 2
            .build()
    ))
    .messages(messages)
    .build();
```

**缓存命中监控**：
```java
Message response = client.messages().create(params);

// 检查缓存使用情况
Usage usage = response.usage();
int cacheRead = usage.cacheReadInputTokens();      // 从缓存读取
int cacheWrite = usage.cacheCreationInputTokens(); // 新写入缓存
int newTokens = usage.inputTokens();               // 未缓存的 token

// 计算节省
int totalInput = cacheRead + cacheWrite + newTokens;
double saved = (double) cacheRead / totalInput * 100;
log.info("Cache hit rate: {}%", saved);
```

**失效条件**：
| 修改 | 影响 |
|------|------|
| Tools 定义 | 全部失效 |
| System Prompt | System + Messages 失效 |
| Images | Messages 失效 |
| tool_choice | Messages 失效 |

**优点**：
- ✅ 成本降低 90%
- ✅ 延迟降低（跳过处理）
- ✅ 不损失上下文

**缺点**：
- ⚠️ 需要模型支持（Claude、部分 OpenAI 模型）
- ⚠️ 需要达到最小 token 数
- ⚠️ TTL 限制（默认 5 分钟，可升级 1 小时）

---

### 1. Claude Code Compaction（服务端压缩）

**原理**：
- 自动在服务端压缩早期对话
- 保留关键信息摘要
- 接近上下文限制（95% 或 25% 剩余）时触发

**触发条件**：
```python
# Claude Code 自动压缩
if context_usage > 0.95:  # 95% 使用率
    auto_compact()
```

**压缩块格式**：
```json
{
  "type": "compaction",
  "summary": "User discussed React performance issues. Key decisions: use useMemo for expensive computations, implement virtual scrolling for long lists..."
}
```

**优点**：
- ✅ 无需客户端改动
- ✅ 自动触发
- ✅ 保持连续性

**缺点**：
- ❌ 依赖服务端支持
- ❌ 早期指令可能丢失

---

### 2. Mem0 Memory Formation（记忆形成）

**原理**：
- 不压缩，而是**选择性记忆**
- 从对话中提取关键事实（facts）
- 存储到独立记忆层

**架构**：
```
对话历史 → Memory Formation → 提取 Facts
              ↓
         Vector Store
              ↓
下一轮对话 → 检索相关 Facts → 注入上下文
```

**示例**：
```
用户：我是 Java 后端开发者，主要用 Spring Boot
     ↓
Mem0 自动提取：
{
  "user_role": "Java backend developer",
  "framework": "Spring Boot",
  "language": "Java"
}
     ↓
下次对话时注入：
[Context] User is a Java backend developer using Spring Boot
```

**效果**：
- 📉 Token 减少 80-90%
- 📈 响应质量提升 26%

**优点**：
- ✅ 精准保留关键信息
- ✅ 跨会话持久化
- ✅ 大幅降低 token 消耗

**缺点**：
- ❌ 需要额外的记忆存储
- ❌ 实现复杂度较高

---

### 3. 滑动窗口 + 摘要（Sliding Window + Summarization）

**原理**：
- 保留最近 N 条消息（完整）
- 早期消息压缩为摘要
- 平衡上下文连续性和 token 消耗

**实现**：
```python
def build_context(messages, window_size=10):
    if len(messages) <= window_size:
        return messages
    
    # 分割
    recent = messages[-window_size:]  # 保留最近 10 条
    older = messages[:-window_size]   # 更早的
    
    # 压缩早期消息
    summary = summarize(older)
    
    return [
        {"role": "system", "content": f"Previous context: {summary}"},
        *recent
    ]
```

**触发条件**：
```python
# 基于 token 数
if token_count(messages) > max_tokens * 0.8:
    trigger_summarization()

# 基于消息数
if len(messages) > 20:
    trigger_summarization()
```

**优点**：
- ✅ 实现简单
- ✅ 可控性强
- ✅ 不依赖服务端

**缺点**：
- ⚠️ 摘要可能丢失细节
- ⚠️ 需要额外 LLM 调用（成本）

---

### 4. 分层记忆架构（Hierarchical Memory）

**原理**：
- 模拟人类记忆的多层次结构
- 不同信息保存不同时长

**架构**：
```
┌─────────────────────────────────┐
│ Working Memory (工作记忆)        │  当前会话
│ - 最近 10-20 条消息              │  立即丢弃
└─────────────────────────────────┘
            ↓ 重要信息提取
┌─────────────────────────────────┐
│ Episodic Memory (情景记忆)       │  最近 7 天
│ - 重要对话片段                   │  定期衰减
└─────────────────────────────────┘
            ↓ 长期沉淀
┌─────────────────────────────────┐
│ Semantic Memory (语义记忆)       │  长期保存
│ - 用户偏好、事实知识             │  永久存储
└─────────────────────────────────┘
```

**示例**：
```java
class HierarchicalMemory {
    // 工作记忆：当前会话，FIFO
    private Deque<Message> workingMemory;  // 最多 20 条
    
    // 情景记忆：最近重要对话，带时间衰减
    private List<Episode> episodicMemory;  // 最多 100 条
    
    // 语义记忆：永久事实
    private KnowledgeBase semanticMemory;  // 无限制
}
```

---

### 5. Token 统计与监控

---

## 🎯 JaguarClaw 优化方案

### 方案选择

基于调研，建议采用**组合策略**：

| 策略 | 优先级 | 效果 | 复杂度 | 说明 |
|------|--------|------|--------|------|
| **Prefix Caching** | P0 | ⭐⭐⭐⭐⭐ | 低 | **最重要！成本降低 90%** |
| **Token 统计** | P0 | ⭐⭐⭐ | 低 | 可监控 |
| **滑动窗口** | P1 | ⭐⭐⭐ | 低 | 限制历史 |
| **智能压缩** | P2 | ⭐⭐⭐⭐ | 中 | 压缩早期对话 |
| **分层记忆** | P3 | ⭐⭐⭐⭐⭐ | 高 | 长期优化 |

---

## 📋 实施方案

### 阶段 0：Prefix Caching（1 天）⭐ 最重要

**目标**：利用 API 缓存，成本降低 90%

#### 0.1 修改 LLM 请求

```java
// LlmRequest.java 添加 cache_control 支持
@Data
public class LlmRequest {
    private List<Map<String, Object>> system;
    private List<LlmRequest.Message> messages;
    private List<Map<String, Object>> tools;
    private String model;
    private Integer maxTokens;
    
    // 新增：缓存控制
    private Map<String, Object> cacheControl;
}
```

#### 0.2 ContextBuilder 启用缓存

```java
// ContextBuilder.java
public LlmRequest buildWithCache(
    String systemPrompt,
    List<Map<String, Object>> tools,
    List<LlmRequest.Message> history,
    String userPrompt,
    boolean enableCache
) {
    List<Map<String, Object>> systemBlocks = new ArrayList<>();
    
    // System prompt (带缓存)
    Map<String, Object> systemBlock = new HashMap<>();
    systemBlock.put("type", "text");
    systemBlock.put("text", systemPrompt);
    if (enableCache) {
        systemBlock.put("cache_control", Map.of("type", "ephemeral"));
    }
    systemBlocks.add(systemBlock);
    
    // Tools (带缓存)
    List<Map<String, Object>> cachedTools = tools;
    if (enableCache && !tools.isEmpty()) {
        // 在最后一个 tool 添加缓存标记
        Map<String, Object> lastTool = new HashMap<>(tools.get(tools.size() - 1));
        lastTool.put("cache_control", Map.of("type", "ephemeral"));
        cachedTools = new ArrayList<>(tools.subList(0, tools.size() - 1));
        cachedTools.add(lastTool);
    }
    
    return LlmRequest.builder()
        .system(systemBlocks)
        .tools(cachedTools)
        .messages(history)
        .cacheControl(enableCache ? Map.of("type", "ephemeral") : null)
        .build();
}
```

#### 0.3 监控缓存效果

```java
// OpenAiCompatibleLlmClient.java
private void recordCacheUsage(LlmResponse response, RunContext context) {
    Usage usage = response.getUsage();
    
    if (usage.getCacheReadInputTokens() != null && usage.getCacheReadInputTokens() > 0) {
        log.info("Cache hit! Read: {}, Write: {}, New: {}",
            usage.getCacheReadInputTokens(),
            usage.getCacheCreationInputTokens(),
            usage.getInputTokens());
        
        // 记录到上下文
        context.setCacheReadTokens(usage.getCacheReadInputTokens());
        context.setCacheWriteTokens(usage.getCacheCreationInputTokens());
        
        // 发布事件
        eventBus.publish(AgentEvent.cacheHit(
            context.getConnectionId(),
            context.getRunId(),
            usage.getCacheReadInputTokens()
        ));
    }
}
```

#### 0.4 配置

```yaml
# application.yml
llm:
  cache:
    enabled: true
    ttl: 5m  # 5m 或 1h
    min-tokens: 1024  # 最小缓存 token 数
```

**效果**：
- 首次请求：正常成本
- 后续请求（5分钟内）：**成本降低 90%**

---

### 阶段 1：Token 统计 + 基础限制（2 天）

**目标**：可监控、可限制

#### 1.1 添加 Token 统计

```java
// RunContext 添加字段
public class RunContext {
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private double totalCost = 0.0;
    
    public void addTokenUsage(int input, int output, double cost) {
        this.totalInputTokens += input;
        this.totalOutputTokens += output;
        this.totalCost += cost;
    }
}
```

#### 1.2 从 LLM 响应提取 usage

```java
// OpenAiCompatibleLlmClient.java 修改
public Flux<LlmChunk> stream(LlmRequest request) {
    return webClient.post()
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(String.class)
        .doOnNext(json -> {
            // 提取 usage（如果存在）
            if (json.contains("\"usage\"")) {
                LlmUsage usage = parseUsage(json);
                if (usage != null && context != null) {
                    context.addTokenUsage(
                        usage.getInputTokens(),
                        usage.getOutputTokens(),
                        calculateCost(usage)
                    );
                    
                    // 发布事件
                    eventBus.publish(AgentEvent.tokenUsage(
                        context.getConnectionId(),
                        context.getRunId(),
                        usage
                    ));
                }
            }
        });
}
```

#### 1.3 添加配置限制

```yaml
# application.yml
llm:
  context:
    max-tokens: 128000      # 最大上下文
    warning-threshold: 0.8  # 80% 警告
    compact-threshold: 0.9  # 90% 触发压缩
    
  budget:
    daily-token-limit: 1000000    # 每日 100 万 tokens
    session-token-limit: 100000   # 每会话 10 万 tokens
    monthly-cost-limit: 100.0     # 每月 $100
```

---

### 阶段 2：滑动窗口（1 天）

**目标**：限制历史消息数量

```java
// ContextBuilder.java 优化
public LlmRequest build(
    String systemPrompt,
    List<LlmRequest.Message> history,
    String userPrompt,
    int maxHistoryMessages  // 新增参数
) {
    List<LlmRequest.Message> messages = new ArrayList<>();
    
    // 1. System prompt
    messages.add(LlmRequest.Message.system(systemPrompt));
    
    // 2. 历史消息（滑动窗口）
    if (history != null && !history.isEmpty()) {
        List<LlmRequest.Message> windowedHistory = applySlidingWindow(
            history, 
            maxHistoryMessages
        );
        messages.addAll(windowedHistory);
    }
    
    // 3. 当前用户输入
    messages.add(LlmRequest.Message.user(userPrompt));
    
    return LlmRequest.builder().messages(messages).build();
}

/**
 * 滑动窗口：保留最近 N 条消息
 */
private List<LlmRequest.Message> applySlidingWindow(
    List<LlmRequest.Message> history,
    int maxMessages
) {
    if (history.size() <= maxMessages) {
        return history;
    }
    
    // 保留最近的消息
    return history.subList(
        history.size() - maxMessages,
        history.size()
    );
}
```

**配置**：
```yaml
llm:
  context:
    max-history-messages: 20  # 保留最近 20 条消息
```

---

### 阶段 3：智能压缩（3 天）

**目标**：自动压缩早期对话

#### 3.1 压缩触发器

```java
@Component
public class ContextCompactor {

    @Value("${llm.context.compact-threshold:0.9}")
    private double compactThreshold;
    
    private final LlmClient llmClient;
    private final TokenCounter tokenCounter;
    
    /**
     * 检查是否需要压缩
     */
    public boolean shouldCompact(List<LlmRequest.Message> messages, int maxTokens) {
        int currentTokens = tokenCounter.count(messages);
        double usage = (double) currentTokens / maxTokens;
        return usage >= compactThreshold;
    }
    
    /**
     * 压缩早期消息
     */
    public CompactResult compact(
        List<LlmRequest.Message> messages,
        int keepRecent  // 保留最近 N 条
    ) {
        if (messages.size() <= keepRecent) {
            return new CompactResult(messages, null);
        }
        
        // 分割
        List<LlmRequest.Message> recent = messages.subList(
            messages.size() - keepRecent,
            messages.size()
        );
        List<LlmRequest.Message> older = messages.subList(
            0,
            messages.size() - keepRecent
        );
        
        // 生成摘要
        String summary = generateSummary(older);
        
        // 构建新消息列表
        List<LlmRequest.Message> compacted = new ArrayList<>();
        compacted.add(LlmRequest.Message.system(
            "Previous conversation summary:\n" + summary
        ));
        compacted.addAll(recent);
        
        return new CompactResult(compacted, summary);
    }
    
    /**
     * 使用 LLM 生成摘要
     */
    private String generateSummary(List<LlmRequest.Message> messages) {
        String prompt = """
            Summarize the following conversation concisely.
            Focus on:
            1. Key decisions made
            2. Important facts mentioned
            3. Unresolved questions
            4. User preferences
            
            Keep it under 500 tokens.
            
            Conversation:
            %s
            """.formatted(formatMessages(messages));
        
        LlmRequest request = LlmRequest.builder()
            .messages(List.of(LlmRequest.Message.user(prompt)))
            .build();
        
        return llmClient.complete(request).block();
    }
}
```

#### 3.2 集成到 ContextBuilder

```java
public LlmRequest build(
    String systemPrompt,
    List<LlmRequest.Message> history,
    String userPrompt
) {
    List<LlmRequest.Message> messages = new ArrayList<>();
    
    // 1. System prompt
    messages.add(LlmRequest.Message.system(systemPrompt));
    
    // 2. 历史消息（带压缩）
    if (history != null && !history.isEmpty()) {
        List<LlmRequest.Message> processedHistory = history;
        
        // 检查是否需要压缩
        if (contextCompactor.shouldCompact(history, maxContextTokens)) {
            CompactResult result = contextCompactor.compact(history, keepRecentMessages);
            processedHistory = result.getMessages();
            
            log.info("Context compacted: {} -> {} messages, saved {} tokens",
                history.size(), processedHistory.size(),
                estimateTokens(history) - estimateTokens(processedHistory));
        }
        
        messages.addAll(processedHistory);
    }
    
    // 3. 当前用户输入
    messages.add(LlmRequest.Message.user(userPrompt));
    
    return LlmRequest.builder().messages(messages).build();
}
```

---

### 阶段 4：Token 预算管理（2 天）

**目标**：可预测、可控成本

```java
@Service
public class TokenBudgetService {

    private final TokenUsageRepository usageRepository;
    
    /**
     * 检查是否超出预算
     */
    public BudgetCheck checkBudget(String connectionId) {
        // 今日使用
        int todayUsage = usageRepository.getTodayUsage(connectionId);
        
        // 本月使用
        double monthCost = usageRepository.getMonthCost(connectionId);
        
        return BudgetCheck.builder()
            .todayTokens(todayUsage)
            .todayLimit(dailyTokenLimit)
            .monthCost(monthCost)
            .monthLimit(monthlyCostLimit)
            .withinBudget(todayUsage < dailyTokenLimit && monthCost < monthlyCostLimit)
            .build();
    }
    
    /**
     * 记录使用
     */
    public void recordUsage(String connectionId, TokenUsage usage) {
        usageRepository.save(TokenUsageEntity.from(connectionId, usage));
        
        // 检查阈值
        if (usage.getTotalTokens() > dailyTokenLimit * 0.8) {
            eventBus.publish(AgentEvent.budgetWarning(
                connectionId,
                "Token usage at 80% of daily limit"
            ));
        }
    }
}
```

---

## 📊 效果预估

### 优化前

```
第 1 轮：1,000 tokens
第 10 轮：10,000 tokens
第 50 轮：50,000 tokens  ← 超过限制
```

### 优化后（滑动窗口 + 压缩）

```
第 1 轮：1,000 tokens
第 10 轮：3,000 tokens（窗口限制）
第 50 轮：5,000 tokens（压缩生效）
```

**节省**：90% token 消耗

---

## 🗓️ 实施计划

| 阶段 | 内容 | 工作量 | 产出 |
|------|------|--------|------|
| **1** | Token 统计 + 基础限制 | 2 天 | 可监控 |
| **2** | 滑动窗口 | 1 天 | 可控制 |
| **3** | 智能压缩 | 3 天 | 高效压缩 |
| **4** | Token 预算管理 | 2 天 | 可预测 |

**总计**：8 天

---

## ✅ 验收标准

1. ✅ 可查看当前会话 token 使用量
2. ✅ 可设置 token 限制并触发警告
3. ✅ 历史消息不超过 N 条
4. ✅ 上下文接近限制时自动压缩
5. ✅ 压缩后保持对话连续性
6. ✅ 可查看每日/每月 token 消耗统计

---

*方案完成时间：2026-03-02 14:30 UTC*
