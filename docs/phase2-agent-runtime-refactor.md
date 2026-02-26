# AgentRuntime 拆分重构方案

> 版本：v1.0
> 日期：2026-02-26
> 作者：Pace（基于 Phase 0-1 审计结果）

---

## 一、问题诊断

### 1.1 现状
- **AgentRuntime.java**：824 行代码
- **职责混杂**：ReAct 循环 + Skill 激活 + SubAgent 屏障 + 工具执行 + HITL 确认 + 文件追踪
- **依赖注入**：12 个依赖项（LlmClient, ToolRegistry, ToolDispatcher, EventBus, LoopConfig, CancellationManager, HitlManager, ContextBuilder, SkillSelector, PreCompactionFlushHook, SubagentCompletionTracker, SessionFileService）
- **测试困难**：核心循环逻辑与太多细节耦合

### 1.2 耦合度分析

| 职责 | 代码行数 | 耦合程度 | 测试难度 |
|------|---------|---------|---------|
| ReAct 循环控制 | ~150 | 高 | 高 |
| LLM 单步调用 | ~80 | 中 | 中 |
| 工具执行 | ~120 | 高 | 高 |
| Skill 激活（自动 + use_tool） | ~100 | 中 | 中 |
| SubAgent 屏障等待 | ~100 | 高 | 高 |
| HITL 确认 | ~50 | 中 | 中 |
| 事件发布 | ~30 | 低 | 低 |
| 文件追踪 | ~40 | 低 | 低 |

### 1.3 核心问题
1. **单一类承担过多职责**（违反 SRP）
2. **难以单独测试各模块**
3. **修改一处可能影响多处**
4. **新增功能需要修改核心类**（违反 OCP）

---

## 二、拆分方案

### 2.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                     AgentRuntime (协调器)                     │
│  职责：组装各组件，编排 ReAct 循环流程                            │
│  依赖：LoopOrchestrator + ToolExecutor + SkillActivator      │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ LoopOrchestrator │ │  ToolExecutor   │ │ SkillActivator  │
│ 循环编排         │ │  工具执行       │ │ Skill 激活      │
│ - 步数控制       │ │ - HITL 确认    │ │ - 自动检测      │
│ - 超时检测       │ │ - 工具分发     │ │ - use_skill     │
│ - 取消检测       │ │ - 结果收集     │ │ - 上下文注入    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                   ┌─────────────────┐
                   │ SubagentBarrier │
                   │ 子代理屏障       │
                   │ - 结果等待      │
                   │ - 超时处理      │
                   └─────────────────┘
```

### 2.2 拆分后的类

#### 2.2.1 AgentRuntime（精简后）
- **行数**：~150 行
- **职责**：组装组件，编排流程
- **依赖**：LoopOrchestrator, ToolExecutor, SkillActivator, SubagentBarrier

#### 2.2.2 LoopOrchestrator（新建）
- **行数**：~200 行
- **职责**：ReAct 循环控制
- **包含**：
  - 步数/超时/取消检测
  - 循环状态管理
  - 事件发布

#### 2.2.3 ToolExecutor（新建）
- **行数**：~150 行
- **职责**：工具执行
- **包含**：
  - HITL 确认流程
  - 工具分发
  - 结果处理
  - 文件追踪

#### 2.2.4 SkillActivator（新建）
- **行数**：~120 行
- **职责**：Skill 激活
- **包含**：
  - 自动检测 `[USE_SKILL:xxx]`
  - `use_skill` 工具拦截
  - 上下文注入
  - 激活限制检查

#### 2.2.5 SubagentBarrier（新建）
- **行数**：~100 行
- **职责**：子代理屏障
- **包含**：
  - subRunId 跟踪
  - 结果等待
  - 超时处理
  - 结果格式化

---

## 三、详细设计

### 3.1 LoopOrchestrator

```java
/**
 * ReAct 循环编排器
 * 负责循环控制：步数、超时、取消检测
 */
@Component
public class LoopOrchestrator {

    private final EventBus eventBus;

    /**
     * 执行循环步骤
     * @return 循环是否应该继续
     */
    public LoopState checkLoopState(RunContext context) {
        // 检查取消
        if (context.isAborted()) {
            return LoopState.cancelled();
        }
        // 检查超时
        if (context.isTimedOut()) {
            return LoopState.timeout();
        }
        // 检查步数
        if (context.isMaxStepsReached()) {
            return LoopState.maxSteps();
        }
        return LoopState.continue_();
    }

    /**
     * 发布步骤完成事件
     */
    public void publishStepEvent(RunContext context) {
        eventBus.publish(AgentEvent.stepCompleted(...));
    }

    public record LoopState(boolean shouldContinue, String reason) {
        static LoopState continue_() { return new LoopState(true, null); }
        static LoopState cancelled() { return new LoopState(false, "cancelled"); }
        static LoopState timeout() { return new LoopState(false, "timeout"); }
        static LoopState maxSteps() { return new LoopState(false, "max_steps"); }
    }
}
```

### 3.2 ToolExecutor

```java
/**
 * 工具执行器
 * 负责 HITL 确认和工具分发
 */
@Component
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolDispatcher toolDispatcher;
    private final HitlManager hitlManager;
    private final EventBus eventBus;
    private final SessionFileService sessionFileService;

    /**
     * 执行工具调用列表
     */
    public List<ToolExecutionResult> executeToolCalls(
            RunContext context,
            List<ToolCall> toolCalls
    ) {
        return toolCalls.stream()
                .map(call -> executeSingleTool(context, call))
                .toList();
    }

    /**
     * 执行单个工具（含 HITL）
     */
    private ToolExecutionResult executeSingleTool(
            RunContext context,
            ToolCall toolCall
    ) {
        // 1. 检查 HITL
        // 2. 发布事件
        // 3. 执行工具
        // 4. 记录文件
        // 5. 返回结果
    }

    public record ToolExecutionResult(String callId, ToolResult result) {}
}
```

### 3.3 SkillActivator

```java
/**
 * Skill 激活器
 * 负责检测和激活 Skill
 */
@Component
public class SkillActivator {

    private final SkillSelector skillSelector;
    private final ContextBuilder contextBuilder;
    private final EventBus eventBus;

    /**
     * 检测自动 Skill 激活
     */
    public Optional<SkillActivation> detectAutoActivation(
            String llmResponse,
            RunContext context
    ) {
        SkillSelection selection = skillSelector.parseFromLlmResponse(
                llmResponse, context.getOriginalInput());

        if (!selection.isSelected()) return Optional.empty();

        // 检查激活限制
        if (context.isSkillActivationLimitReached(selection.getSkillName())) {
            return Optional.empty();
        }

        return Optional.of(new SkillActivation(
                selection.getSkillName(),
                "auto"
        ));
    }

    /**
     * 检测 use_skill 工具激活
     */
    public Optional<SkillActivation> detectToolActivation(
            List<ToolCall> toolCalls,
            RunContext context
    ) {
        // 遍历 tool_calls，找 use_skill
    }

    /**
     * 应用 Skill 激活
     */
    public Optional<SkillAwareRequest> applyActivation(
            SkillActivation activation,
            List<LlmRequest.Message> history,
            String originalInput
    ) {
        // 调用 contextBuilder.handleSkillActivationByName
    }

    public record SkillActivation(String skillName, String triggerType) {}
}
```

### 3.4 SubagentBarrier

```java
/**
 * 子代理屏障
 * 等待所有 spawn 的子代理完成
 */
@Component
public class SubagentBarrier {

    private final SubagentCompletionTracker tracker;

    /**
     * 等待子代理完成
     */
    public BarrierResult waitForCompletion(
            List<String> subRunIds,
            RunContext context
    ) {
        List<SubagentResult> results = new ArrayList<>();

        for (String subRunId : subRunIds) {
            try {
                SubagentResult result = tracker.getFuture(subRunId)
                        .get(timeoutSeconds, TimeUnit.SECONDS);
                results.add(result);
            } catch (TimeoutException e) {
                results.add(SubagentResult.timeout(subRunId));
            }
        }

        return new BarrierResult(results, formatResults(results));
    }

    /**
     * 格式化结果为 LLM 消息
     */
    private String formatResults(List<SubagentResult> results) {
        // 生成 [All spawned SubAgents have completed...] 消息
    }

    public record BarrierResult(List<SubagentResult> results, String formattedMessage) {}
}
```

---

## 四、迁移策略

### 4.1 渐进式拆分（风险最小化）

**阶段 1**：创建新类，但不修改 AgentRuntime
- 创建 LoopOrchestrator（空实现）
- 创建 ToolExecutor（空实现）
- 创建 SkillActivator（空实现）
- 创建 SubagentBarrier（空实现）
- **目的**：建立骨架，验证编译

**阶段 2**：迁移单一职责
- 迁移 SubAgent 屏障逻辑 → SubagentBarrier
- **目的**：最独立的部分，风险最低

**阶段 3**：迁移工具执行
- 迁移工具执行逻辑 → ToolExecutor
- **目的**：测试 HITL 流程

**阶段 4**：迁移 Skill 激活
- 迁移 Skill 激活逻辑 → SkillActivator
- **目的**：测试 Skill 激活

**阶段 5**：迁移循环控制
- 迁移循环控制逻辑 → LoopOrchestrator
- **目的**：完成核心拆分

### 4.2 测试策略

每个阶段完成后：
1. 运行现有测试确保无回归
2. 为新类添加单元测试
3. 集成测试验证完整流程

### 4.3 回滚机制

- 每个阶段完成后打 tag
- 发现问题立即回滚到上一个稳定版本

---

## 五、预期收益

### 5.1 代码质量

| 指标 | 拆分前 | 拆分后 |
|------|--------|--------|
| AgentRuntime 行数 | 824 | ~150 |
| 最大类行数 | 824 | ~200 |
| 平均方法长度 | 40+ | 15-20 |
| 单元测试覆盖率 | 低 | 高 |

### 5.2 可维护性
- **单一职责**：每个类只做一件事
- **易于测试**：可以单独测试每个组件
- **易于扩展**：新增功能不需要修改核心类
- **易于理解**：新人可以快速理解各组件

### 5.3 性能影响
- **无**：只是代码组织变化，运行时行为不变

---

## 六、风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 引入 bug | 中 | 高 | 渐进式迁移 + 完整测试 |
| 性能下降 | 低 | 中 | 保持相同执行路径 |
| 依赖注入循环 | 低 | 中 | 仔细设计依赖关系 |
| 破坏现有 API | 低 | 高 | 保持 public 方法签名 |

---

## 七、时间估算

| 阶段 | 工作量 | 风险 |
|------|--------|------|
| 阶段 1：骨架创建 | 0.5 天 | 低 |
| 阶段 2：SubAgent 屏障 | 1 天 | 低 |
| 阶段 3：工具执行 | 1 天 | 中 |
| 阶段 4：Skill 激活 | 1 天 | 中 |
| 阶段 5：循环控制 | 1 天 | 中 |
| 测试 + 修复 | 1 天 | 中 |
| **总计** | **5.5 天** | - |

---

## 八、决策请求

请 jaguar 审核以下决策点：

1. **是否采用渐进式拆分策略？**（推荐：是）
2. **是否保持 AgentRuntime 的 public API 不变？**（推荐：是）
3. **是否为每个新类创建完整的单元测试？**（推荐：是）
4. **是否需要在拆分前先补充现有功能的集成测试？**（推荐：是）

---

**等待 jaguar 审核后，指挥 Codex 执行**
