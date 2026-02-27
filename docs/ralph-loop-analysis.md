# Ralph Loop vs 当前 ReAct 实现分析

> 日期：2026-02-26
> 问题：是否在拆分中直接迭代到 Ralph Loop？

---

## 一、Ralph Loop 核心概念

### 1.1 核心差异

| 特性 | 当前 ReAct | Ralph Loop |
|------|-----------|------------|
| **终止条件** | LLM 自己决定 | 外部验证 `verifyCompletion()` |
| **可靠性** | 低（LLM "差不多"就停） | 高（可验证的标准） |
| **资源限制** | maxSteps | maxSteps + maxTokens + maxCost |
| **上下文管理** | 简单追加 | L1 sticky + 智能压缩 |

### 1.2 Ralph Loop 关键接口

```java
// 外部验证
interface TaskCompletion {
    CompletableFuture<VerificationResult> verifyCompletion();
    int getMaxIterations();
    int getMaxTokens();
    double getMaxCost();
}

record VerificationResult(boolean complete, String reason) {}

// 终止控制器
interface TerminationController {
    CompletableFuture<StopDecision> shouldStop(RunContext context);
}

record StopDecision(boolean stop, String reason, StopTrigger trigger) {}
```

---

## 二、当前 JaguarClaw 实现分析

### 2.1 已有能力

| 能力 | 状态 | 位置 |
|------|------|------|
| ReAct 循环 | ✅ | AgentRuntime.doExecuteLoop() |
| 步数限制 | ✅ | LoopConfig.maxSteps |
| 超时限制 | ✅ | LoopConfig.runTimeoutSeconds |
| 取消检测 | ✅ | CancellationManager |
| 工具执行 | ✅ | ToolDispatcher |
| HITL 确认 | ✅ | HitlManager |
| Skill 激活 | ✅ | SkillSelector + ContextBuilder |
| SubAgent 屏障 | ✅ | SubagentCompletionTracker |

### 2.2 缺失能力

| 能力 | Ralph Loop 需要 | 当前状态 |
|------|----------------|---------|
| **外部验证** | verifyCompletion() | ❌ 无 |
| **Token 计数** | maxTokens | ❌ 无（LLM 返回值未追踪） |
| **成本追踪** | maxCost | ❌ 无 |
| **L1 Sticky** | 智能上下文 | ⚠️ 部分有（渐进式加载 L0/L1） |
| **结构化 Trace** | 完整追踪 | ⚠️ 部分有（EventBus） |

---

## 三、迁移到 Ralph Loop 的前置条件

### 3.1 必须完成（阻塞性）

1. **Token 使用追踪**
   - LLM 响应中提取 `usage.input_tokens` / `usage.output_tokens`
   - 累计到 RunContext
   - 当前：无

2. **成本计算**
   - 基于 model + token 数计算成本
   - 需要维护价格表
   - 当前：无

3. **TaskCompletion 接口设计**
   - 定义 `verifyCompletion()` 接口
   - 集成到终止条件检查
   - 当前：无

### 3.2 建议完成（增强性）

4. **L1 Sticky 机制**
   - 已有 Phase 1 渐进式加载
   - 需要智能选择哪些 L1 常驻
   - 当前：部分有

5. **结构化 Trace 输出**
   - 已有 EventBus
   - 需要聚合输出
   - 当前：部分有

---

## 四、决策分析

### 4.1 方案 A：先拆分，后迭代

**优点**：
- 风险可控，每步目标明确
- 拆分后更容易添加 Ralph Loop 特性
- 可以先验证拆分效果

**缺点**：
- 需要两次修改 LoopOrchestrator
- 可能产生冗余工作

**工作量**：
- 拆分：5.5 天
- Ralph Loop：3 天
- **总计：8.5 天**

### 4.2 方案 B：拆分 + Ralph Loop 同步

**优点**：
- 一次性完成架构升级
- LoopOrchestrator 直接支持外部验证
- 减少重复修改

**缺点**：
- 变更范围大，风险较高
- 需要先完成 Token 追踪等前置

**工作量**：
- 前置（Token + Cost）：1 天
- 拆分 + Ralph Loop：6 天
- **总计：7 天**

### 4.3 方案 C：最小 Ralph Loop，后拆分

**优点**：
- 先验证 Ralph Loop 价值
- 最小改动

**缺点**：
- 在 824 行的类上添加逻辑更混乱
- 可能影响后续拆分

**不推荐**

---

## 五、我的建议

### 5.1 推荐方案：A（先拆分，后迭代）

**理由**：

1. **前置条件不足**
   - Token 追踪、成本计算都需要先实现
   - 这些不是拆分的依赖，可以独立完成

2. **降低认知负担**
   - 拆分后代码结构清晰
   - 再添加 Ralph Loop 特性更容易理解

3. **渐进式风险控制**
   - 每个阶段可独立测试
   - 问题更容易定位

### 5.2 执行路径

```
Phase 2a: AgentRuntime 拆分（5.5 天）
    ↓
Phase 2b: Token + Cost 追踪（1 天）
    ↓
Phase 2c: TaskCompletion + Ralph Loop（2 天）
    ↓
完成
```

### 5.3 关键卡点

| 卡点 | 解决方案 | 工作量 |
|------|---------|--------|
| LLM 响应 token 提取 | 修改 LlmClient 返回值 | 0.5 天 |
| 成本计算 | 维护价格表 + 计算逻辑 | 0.5 天 |
| TaskCompletion 接口 | 设计 + 集成到 LoopOrchestrator | 1 天 |
| L1 Sticky | 基于渐进式加载扩展 | 1 天 |

---

## 六、如果坚持同步进行

### 6.1 最小前置集

必须先完成：
1. Token 追踪（修改 LlmClient）
2. TaskCompletion 接口定义

### 6.2 修改后的拆分

```java
// LoopOrchestrator 直接支持 Ralph Loop
@Component
public class LoopOrchestrator {

    private final TerminationController terminationController;
    private final EventBus eventBus;

    /**
     * 检查循环状态（支持外部验证）
     */
    public LoopState checkLoopState(RunContext context) {
        // 1. 基础检查（取消/超时/步数）
        LoopState baseState = checkBasicState(context);
        if (!baseState.shouldContinue()) {
            return baseState;
        }

        // 2. Ralph Loop: 外部验证
        if (context.getTaskCompletion() != null) {
            VerificationResult result = context.getTaskCompletion()
                    .verifyCompletion().join();
            if (result.complete()) {
                return LoopState.completed(result.reason());
            }
        }

        // 3. Ralph Loop: 资源限制
        if (context.isMaxTokensReached() || context.isMaxCostReached()) {
            return LoopState.resourceLimit();
        }

        return LoopState.continue_();
    }
}
```

### 6.3 RunContext 扩展

```java
@Getter
public class RunContext {
    // ... 现有字段 ...

    // Ralph Loop 新增
    private final TaskCompletion taskCompletion;
    private int totalInputTokens;
    private int totalOutputTokens;
    private double totalCost;

    public boolean isMaxTokensReached() {
        if (taskCompletion == null) return false;
        return totalInputTokens + totalOutputTokens >= taskCompletion.getMaxTokens();
    }

    public boolean isMaxCostReached() {
        if (taskCompletion == null) return false;
        return totalCost >= taskCompletion.getMaxCost();
    }

    public void addTokenUsage(int input, int output) {
        this.totalInputTokens += input;
        this.totalOutputTokens += output;
    }
}
```

---

## 七、最终决策请求

请 jaguar 选择：

**A. 先拆分，后 Ralph Loop（推荐）**
- 优点：风险低，渐进式
- 缺点：需要两次修改
- 时间：8.5 天

**B. 拆分 + Ralph Loop 同步**
- 优点：一次完成
- 缺点：需要先完成 Token 追踪前置
- 时间：7 天

**C. 其他方案**
- 请说明

---

**我的推荐：方案 A**
- 先完成拆分，代码结构清晰
- 再添加 Token 追踪 + Ralph Loop
- 渐进式风险控制
