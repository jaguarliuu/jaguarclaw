# Skill 命中率与执行质量改造设计（方案 B）

## 1. 背景与问题

当前系统已经具备 Skill 加载、索引注入和 `use_skill` 工具激活能力，但线上体验存在两个核心问题：

1. Skill 命中率不稳定（模型经常不触发或触发错误 skill）
2. Skill 触发后执行质量不稳定（同一轮工具调用存在“先执行普通工具、后激活 skill”的时序问题）

根因（结合现有代码与 AgentScope 对比）主要有：

- 仍保留文本标记通道（`[USE_SKILL:xxx]`），可靠性弱
- `use_skill.skill_name` 为自由字符串，不是枚举，模型容易拼错
- `AgentRuntime` 中 skill 检测发生在工具执行之后，导致激活滞后
- Skill 索引信息粒度偏粗（仅 name/description），触发线索不足
- 缺少专门的命中率与激活时序观测指标

## 2. 目标

本次改造优先实现：

1. 统一为工具触发（Tool-first），移除文本标记触发依赖
2. 保证 `use_skill` 前置激活（激活完成后再进入下一轮工具执行）
3. 提升 skill 选择准确度（参数枚举化 + 索引增强）
4. 建立可观测指标，能量化命中率改进

## 3. 非目标

- 不做 Spring AI / AgentScope 框架迁移
- 不重构现有 EventBus/前端事件协议
- 不改变 skill 文件格式的兼容性（保持现有 SKILL.md 可用）

## 4. 方案概览

### 4.1 触发通道收敛（Tool-first）

- 关闭 `LLM 文本标记 -> Skill 激活` 通道（`[USE_SKILL:xxx]`）
- 保留并强化 `use_skill` 工具通道
- slash 命令（`/skill-name ...`）继续保留，作为显式用户触发

### 4.2 激活时序前移（Pre-Execute Activation）

在 `AgentRuntime` 的 tool_calls 分支中：

1. 先检测是否存在 `use_skill`
2. 若存在且当前尚未激活 skill：
   - 立即构建 skill 上下文
   - 发布 skill 激活事件
   - 直接进入下一轮 LLM（不执行本轮其它普通工具）
3. 无 `use_skill` 时按原流程执行工具

结果：杜绝“先 write_file/shell，再激活 skill”的质量退化。

### 4.3 skill_name 枚举化

`UseSkillTool` 动态读取当前 agent 可用 skill 列表，将 `skill_name` 参数改为带 `enum` 的 schema，减少幻觉和拼写错误。

### 4.4 索引增强（不增大过多 token）

在 `SkillMetadata` 增加可选字段（兼容老 skill）：

- `tags`: 关键词列表
- `triggers`: 任务触发短语列表
- `examples`: 1-2 条简短示例

`SkillIndexBuilder` 在预算内优先输出：`name + description + tags/triggers(截断)`，提高召回。

### 4.5 可观测性

新增埋点/日志维度：

- `skill.candidate`（出现匹配候选）
- `skill.activated`（成功激活）
- `skill.activation_skipped`（已激活或限制导致跳过）
- `skill.late_activation_prevented`（检测到含 use_skill 的混合 tool calls 并前置处理）

## 5. 架构改动点

### 5.1 Runtime

- `AgentRuntime`
  - 移除 no-tool 分支中的文本自动激活逻辑
  - 将 `use_skill` 激活检测前移至工具执行前
  - 统一 skill 激活计数策略（tool/slash 路径一致）

- `SkillActivator`
  - 主职责聚焦为工具触发激活
  - 文本标记相关方法保留兼容一版（可标记 deprecated），后续删除

### 5.2 Tool Schema

- `UseSkillTool`
  - `skill_name` 参数增加动态 `enum`
  - 描述文案补充“若匹配 skill 必须先调用”

### 5.3 Skill Metadata & Index

- `SkillMetadata` / `SkillValidator` / `SkillParser`
  - 增加 `tags/triggers/examples` 可选字段解析与校验

- `SkillIndexBuilder`
  - 输出结构加入触发线索，控制 token budget

## 6. 风险与回滚

### 风险

1. 移除文本触发后，极端情况下模型不调 `use_skill` 会显性暴露
2. 前置激活会改变原有“同轮多工具执行”路径
3. 索引增强可能带来 prompt token 增量

### 缓解

- 保留 slash 显式触发兜底
- 加强 `use_skill` schema 约束与 system prompt 规则
- 通过 token budget 截断索引增强字段
- 功能开关预留（`skills.tool-first-only`）用于灰度

### 回滚

- 通过配置开关恢复旧行为（文本触发 + 原时序）
- 代码级回滚点：`AgentRuntime` tool_calls 分支 / `UseSkillTool` schema

## 7. 验收标准

1. 同轮返回 `use_skill + write_file` 时，必须先激活 skill，再下一轮执行写文件
2. `use_skill.skill_name` 的 schema 中包含当前可用 skills 枚举
3. 移除文本标记触发后核心流程可用（slash + tool）
4. 关键埋点可在日志观察到激活路径与跳过原因
5. 编译通过，核心测试通过

## 8. 任务拆分（执行顺序）

### P0（本轮）

- [ ] T1 Runtime：前置 `use_skill` 激活，移除文本自动激活主路径
- [ ] T2 Tool：`UseSkillTool` 参数 `skill_name` 枚举化
- [ ] T3 Runtime：统一 skill 激活计数与日志
- [ ] T4 测试：更新/新增与文本触发解耦后的单测

### P1（下一轮）

- [ ] T5 Metadata：新增 `tags/triggers/examples` 字段（解析 + 校验 + DTO）
- [ ] T6 Index：Skill 索引增强并受 token budget 控制
- [ ] T7 观测：新增 skill 命中率/激活路径埋点
- [ ] T8 评测：构建 20~30 条 skill 触发回放集并输出命中率前后对比

### P2（可选）

- [ ] T9 新工具：`load_skill_resource(skill_name, path)` 按需加载 skill 资源
- [ ] T10 策略：按策略/agent 动态排序 skill 候选，进一步提高召回

## 9. 当前执行决定

已确认采用方案 B，先完成 P0 再进入 P1。

