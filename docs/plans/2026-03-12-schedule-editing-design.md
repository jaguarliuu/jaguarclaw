# 定时任务可编辑能力设计

**日期**: 2026-03-12

## 背景

当前系统中的定时任务支持两种创建来源：

- 用户在设置页手动创建
- 大模型通过 `create_schedule` 工具创建

但任务一旦创建，后续只支持查看、启停、立即执行、删除，不支持手动修改配置。这会带来两个直接问题：

- 大模型自动创建的任务可能存在 `cron`、`prompt`、投递目标等配置缺陷，用户无法修正
- 用户手动创建后若需求变化，也只能删除重建，导致任务身份与最近执行状态丢失

## 目标

为定时任务补齐“创建后可手动编辑”的完整能力，支持用户在设置页对已有任务进行重新配置，而不需要删除重建。

## 非目标

本次不包含以下内容：

- 不新增“任务历史执行记录列表”
- 不支持修改系统运行事实字段
- 不改动大模型侧 `create_schedule` 工具的语义

## 可编辑字段

允许用户修改以下配置字段：

- `name`
- `cronExpr`
- `prompt`
- `targetType`
- `targetRef`
- `emailTo`
- `emailCc`
- `enabled`

以下字段保持只读，由系统维护：

- `id`
- `createdAt`
- `updatedAt`
- `lastRunAt`
- `lastRunSuccess`
- `lastRunError`

这样可以保证用户能修正任务配置，同时不破坏排障与审计所依赖的运行事实。

## 后端设计

### RPC

新增 RPC 方法：`schedule.update`

请求体：

```json
{
  "id": "task-id",
  "name": "日报推送",
  "cronExpr": "0 9 * * *",
  "prompt": "每天 9 点汇总昨日工单并发送给团队",
  "targetType": "webhook",
  "targetRef": "ops-alert",
  "emailTo": null,
  "emailCc": null,
  "enabled": true
}
```

响应体沿用现有 `schedule.create` 的 DTO 结构，返回最新任务配置与最近执行状态。

### Service

在 `ScheduledTaskService` 中新增 `update(...)` 能力，处理流程如下：

1. 按 `id` 查询任务，不存在则抛错
2. 校验所有可编辑字段，规则与创建时一致
3. 原地更新实体，仅修改配置字段
4. 取消已有调度句柄
5. 若更新后的 `enabled=true`，按新配置重新注册调度
6. 返回持久化后的任务实体

### 调度语义

- 更新是“原地更新”，不新建任务，不改变 `id`
- 更新后保留 `lastRunAt`、`lastRunSuccess`、`lastRunError`
- 若 `cronExpr` 非法，则整个更新失败，不接受“数据库改了但调度未重建”的半成功状态
- 若更新后 `enabled=false`，则仅取消已有调度，不重新注册

### 授权

`schedule.update` 应归类为 WRITE 权限，与 `schedule.create/delete/toggle/run` 保持一致。

## 前端设计

设置页的定时任务区域采用“新增 / 编辑复用同一表单”的方案。

### 交互

- 列表卡片新增 `编辑` 按钮
- 点击后打开现有表单面板，进入编辑态
- 表单预填当前任务的全部可编辑字段
- 提交按钮文案切换为“保存修改”
- 保存时调用 `schedule.update`
- 成功后关闭表单并刷新列表
- 取消时退出编辑态并重置表单

### 设计取舍

不采用卡片内联编辑，原因如下：

- 字段较多，且存在 webhook / email 条件分支
- 当前面板式表单已存在，复用成本最低
- 内联编辑更容易引入列表状态混乱和表单同步问题

## 测试策略

### 后端自动测试

新增以下覆盖：

- `ScheduledTaskService` 更新成功
- 已启用任务修改后能正确取消旧调度并注册新调度
- 修改为禁用时只取消调度不重建
- 非法 `id` 或非法参数时抛出预期错误
- `schedule.update` handler 的参数校验和成功响应
- `RpcAuthorizationService` 将 `schedule.update` 归类到 WRITE

### 前端验证

优先通过实现后的手工验证覆盖：

- 新建任务后进入编辑并保存
- webhook / email 两类目标都能编辑
- 编辑后启停状态按保存值生效
- 非法输入时展示错误信息

当前仓库没有现成的定时任务前端测试基建，本次不额外引入新的组件测试框架。
