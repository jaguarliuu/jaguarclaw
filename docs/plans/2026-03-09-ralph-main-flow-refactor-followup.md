# Ralph Main Flow Refactor Follow-up

**Status:** follow-up backlog after the 2026-03-09 main-flow refactor batch

---

## 1. 已完成的主改造

本轮已经完成的核心重构包括：

1. 顶层路由收敛为 `DIRECT` / `REACT`
2. `ContextBuilder` 显式拆分为 direct / react 两个入口
3. 引入内存态 `ExecutionPlan`
4. `Decision` 升级为 item 级 action 模型
5. `AgentRuntime` 开始按 plan item 推进，而不是过早停机
6. HITL 拒绝会阻塞当前 item
7. subagent 派发会回写到 plan item
8. 默认运行时决策装配改为 `DecisionEngine`

---

## 2. 仍然建议继续做的收尾项

### 2.1 命名和兼容层清理

建议继续清理以下历史兼容残留：

- `ContextBuilder.buildForPolicyDecision(...)`
  - 目前更像兼容入口
  - 可以继续减少调用点，最终收敛为 direct / react 两个公开入口
- `TaskComplexity`
  - 当前仍保留历史语义
  - 顶层路由已经不再依赖它表达主流程分流
- `TaskRoutingDecision.complexity`
  - 还可继续降级为兼容字段，而不是核心字段

### 2.2 HITL 结果结构化升级

当前 HITL 拒绝已经能阻塞 plan item，但后续仍建议：

- 明确区分“用户拒绝”“用户超时”“需要再次确认”
- 让前端弹窗返回更稳定的结构化结果
- 避免 runtime 再去猜测 UI 侧意图

### 2.3 ASK_USER 的前后端契约

当前 `ASK_USER` 已进入决策模型，但要形成完整闭环，还需要：

- 前端明确承载 `pendingQuestion`
- 用户反馈能够带着 run 上下文重新进入执行态
- 尽量避免退化为“聊天里继续追问”的弱契约

### 2.4 subagent item 恢复

当前 delegated item 已能记录 subRunId，后续可继续完善：

- 子任务完成后更精确地回填主 plan item
- 支持子任务失败 / 部分成功的 item 级归因
- 允许主 runtime 基于 subagent outcome 继续推进，而不是只注入文本摘要

### 2.5 plan 持久化

当前 plan 是内存态，下一阶段如果要支持更长任务，可以继续做：

- run 级 plan 持久化
- 恢复后继续从当前 item 执行
- plan revision 与冲突保护

但这应当是下一阶段工作，不是当前必要前置。

---

## 3. 不建议做的方向

后续重构时，应继续避免以下方向：

- 不要把顶层路由重新做成复杂状态机
- 不要把工具能力绑定到路由类型上做硬裁剪
- 不要引入依赖正则、命令名、关键词的主决策逻辑
- 不要把 HITL 设计成聊天问答式暂停
- 不要为 plan kernel 过早引入重型工作流框架

---

## 4. 下一阶段建议顺序

建议后续按这个顺序推进：

1. 清理兼容入口与历史命名
2. 完成 `ASK_USER` / HITL 的前后端结构化契约
3. 强化 subagent 与 plan item 的闭环回填
4. 最后再考虑 plan 持久化

这个顺序的原因是：

- 前三项直接影响主流程一致性
- plan 持久化属于增强项，不应打断当前轻量主架构

---

## 5. 当前结论

当前主流程已经完成从“混合分支 + 多点停机”到“轻路由 + 单执行主链 + plan 推进”的关键切换。

因此，后续工作的重点不再是重新发明主流程，而是：

- 清兼容层
- 补前后端契约
- 强化长任务闭环

