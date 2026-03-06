# Logging Architecture Design

**Date:** 2026-03-06

**Goal:** 将当前混在单文件中的桌面端与后端日志拆分为按职责分流的本地文件，并统一加入滚动归档与保留策略，降低 Windows 现场问题排查成本。

**Current State**
- Electron 主进程把启动、运行时诊断、Java stdout/stderr 转发全部写入同一个 `startup.log`。
- Spring Boot 后端默认写入单个 `data/logs/jaguarclaw.log`，虽有基础滚动配置，但没有按职责拆分。
- 结果是日志来源混杂、定位困难、启动失败与运行期问题难以快速区分。

**Constraints**
- 仅改造本地文件日志，不增加 UI 日志查看器。
- 保持现有 `JAGUARCLAW_CONFIG_DIR` / Electron app data 目录约定。
- 默认保留策略：单文件 `10MB`，保留 `7` 天，最多 `20` 个归档。

**Chosen Approach**
- Electron：新增统一日志目录管理与简单滚动归档工具，拆分为：
  - `startup.log`：启动链路、健康检查、runtime 发现；
  - `desktop.log`：Electron 主进程常规事件；
  - `backend-bridge.log`：Java stdout/stderr 桥接输出。
- Spring Boot：引入 `logback-spring.xml`，拆分为：
  - `app.log`：默认业务日志；
  - `rpc.log`：RPC/网关入口相关；
  - `runtime.log`：runtime / skills / shell / browser 相关。
- 启动时将所有日志文件路径写入 `startup.log`，让用户知道应该看哪个文件。

**Why This Approach**
- 直接解决“全打在一个文件里”与“难找”的核心问题。
- 范围适中，不引入新的存储系统或前端复杂度。
- Electron 端与 Spring 端都可独立滚动归档，利于 Windows 现场长期运行。

**Risks / Notes**
- Logger 分类基于包名与现有输出路径，首次拆分可能仍有少量日志落在 `app.log`；后续可迭代细化。
- Electron 自定义轮转先实现最小方案（按大小 + 日期/数量清理），避免新增第三方依赖。
