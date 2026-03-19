# Schedule Run History Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为定时任务添加完整执行记录：独立的 `schedule_run_logs` 表保存每次执行历史，同时打通现有 AuditLog，并改善前端"立即执行"的交互反馈。

**Architecture:**
- 新增 `ScheduleRunLogEntity` / `ScheduleRunLogService` 负责记录每次执行的生命周期（RUNNING → SUCCESS/FAILED）
- `ScheduledTaskExecutor` 在执行开始时写入 RUNNING 记录，完成后更新为 SUCCESS/FAILED，同时调用 `AuditLogService.logScheduleExecution`
- 前端新增 `schedule.runs.list` RPC 调用，每个任务卡片展示最近执行历史；"立即执行"后立即刷新历史，用户可见 RUNNING 状态

**Tech Stack:** Spring Boot 3 / JPA / Flyway, Vue 3 + TypeScript, WebSocket RPC

---

## Chunk 1: 后端 — 执行记录表 + Service

### Task 1: 数据库迁移 — 新增 `schedule_run_logs` 表

**Files:**
- Create: `src/main/resources/db/migration/V26__schedule_run_logs.sql`
- Create: `src/main/resources/db/migration-sqlite/V26__schedule_run_logs.sql`

- [ ] **Step 1: 创建 PostgreSQL 迁移文件**

```sql
-- src/main/resources/db/migration/V26__schedule_run_logs.sql
CREATE TABLE IF NOT EXISTS schedule_run_logs (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    task_name       VARCHAR(200) NOT NULL,
    triggered_by    VARCHAR(20) NOT NULL,   -- 'scheduled' | 'manual'
    status          VARCHAR(20) NOT NULL,   -- 'running' | 'success' | 'failed'
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     INTEGER,
    error_message   TEXT,
    session_id      VARCHAR(36),
    run_id          VARCHAR(36),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_schedule_run_logs_task_id ON schedule_run_logs(task_id);
CREATE INDEX IF NOT EXISTS idx_schedule_run_logs_started_at ON schedule_run_logs(started_at DESC);
```

- [ ] **Step 2: 创建 SQLite 迁移文件（内容相同，去掉 IF NOT EXISTS 限定）**

```sql
-- src/main/resources/db/migration-sqlite/V26__schedule_run_logs.sql
CREATE TABLE IF NOT EXISTS schedule_run_logs (
    id              VARCHAR(36) PRIMARY KEY,
    task_id         VARCHAR(36) NOT NULL,
    task_name       VARCHAR(200) NOT NULL,
    triggered_by    VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     INTEGER,
    error_message   TEXT,
    session_id      VARCHAR(36),
    run_id          VARCHAR(36),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_schedule_run_logs_task_id ON schedule_run_logs(task_id);
CREATE INDEX IF NOT EXISTS idx_schedule_run_logs_started_at ON schedule_run_logs(started_at DESC);
```

- [ ] **Step 3: 验证迁移文件格式正确（与现有 V25 文件格式对比）**

---

### Task 2: ScheduleRunLogEntity + Repository

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogRepository.java`

- [ ] **Step 1: 创建 Entity**

```java
// src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogEntity.java
package com.jaguarliu.ai.schedule;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_run_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRunLogEntity {

    @Id
    private String id;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 'scheduled' | 'manual' */
    @Column(name = "triggered_by", nullable = false, length = 20)
    private String triggeredBy;

    /** 'running' | 'success' | 'failed' */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: 创建 Repository**

```java
// src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogRepository.java
package com.jaguarliu.ai.schedule;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunLogRepository extends JpaRepository<ScheduleRunLogEntity, String> {

    List<ScheduleRunLogEntity> findByTaskIdOrderByStartedAtDesc(String taskId, Pageable pageable);

    List<ScheduleRunLogEntity> findTop20ByOrderByStartedAtDesc();
}
```

---

### Task 3: ScheduleRunLogService

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogService.java`
- Test: `src/test/java/com/jaguarliu/ai/schedule/ScheduleRunLogServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
// src/test/java/com/jaguarliu/ai/schedule/ScheduleRunLogServiceTest.java
package com.jaguarliu.ai.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScheduleRunLogServiceTest {

    private ScheduleRunLogRepository repo;
    private ScheduleRunLogService service;

    @BeforeEach
    void setUp() {
        repo = mock(ScheduleRunLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ScheduleRunLogService(repo);
    }

    @Test
    void startLog_createsRunningEntry() {
        var log = service.start("task-1", "Daily Report", "manual", null, null);

        assertThat(log.getStatus()).isEqualTo("running");
        assertThat(log.getTaskId()).isEqualTo("task-1");
        assertThat(log.getTriggeredBy()).isEqualTo("manual");
        assertThat(log.getStartedAt()).isNotNull();
        verify(repo).save(log);
    }

    @Test
    void completeLog_updatesStatusAndDuration() {
        var log = ScheduleRunLogEntity.builder()
                .id("log-1").taskId("t1").taskName("T").triggeredBy("scheduled")
                .status("running").startedAt(LocalDateTime.now().minusSeconds(5))
                .build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.complete(log, "session-1", "run-1");

        assertThat(log.getStatus()).isEqualTo("success");
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getDurationMs()).isGreaterThan(0);
        verify(repo).save(log);
    }

    @Test
    void failLog_updatesStatusAndError() {
        var log = ScheduleRunLogEntity.builder()
                .id("log-1").taskId("t1").taskName("T").triggeredBy("scheduled")
                .status("running").startedAt(LocalDateTime.now())
                .build();
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.fail(log, "Connection refused");

        assertThat(log.getStatus()).isEqualTo("failed");
        assertThat(log.getErrorMessage()).isEqualTo("Connection refused");
        verify(repo).save(log);
    }
}
```

- [ ] **Step 2: 运行测试，确认 FAIL（类不存在）**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -pl . -Dtest=ScheduleRunLogServiceTest -q 2>&1 | tail -20
```

Expected: 编译错误 `cannot find symbol: class ScheduleRunLogService`

- [ ] **Step 3: 实现 Service**

```java
// src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogService.java
package com.jaguarliu.ai.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleRunLogService {

    private final ScheduleRunLogRepository repository;

    public ScheduleRunLogEntity start(String taskId, String taskName,
                                      String triggeredBy,
                                      String sessionId, String runId) {
        ScheduleRunLogEntity entry = ScheduleRunLogEntity.builder()
                .taskId(taskId)
                .taskName(taskName)
                .triggeredBy(triggeredBy)
                .status("running")
                .startedAt(LocalDateTime.now())
                .sessionId(sessionId)
                .runId(runId)
                .build();
        return repository.save(entry);
    }

    public void complete(ScheduleRunLogEntity entry, String sessionId, String runId) {
        LocalDateTime now = LocalDateTime.now();
        entry.setStatus("success");
        entry.setFinishedAt(now);
        entry.setDurationMs((int) ChronoUnit.MILLIS.between(entry.getStartedAt(), now));
        if (sessionId != null) entry.setSessionId(sessionId);
        if (runId != null) entry.setRunId(runId);
        repository.save(entry);
    }

    public void fail(ScheduleRunLogEntity entry, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        entry.setStatus("failed");
        entry.setFinishedAt(now);
        entry.setDurationMs((int) ChronoUnit.MILLIS.between(entry.getStartedAt(), now));
        entry.setErrorMessage(errorMessage);
        repository.save(entry);
    }

    public List<ScheduleRunLogEntity> listByTask(String taskId, int limit) {
        return repository.findByTaskIdOrderByStartedAtDesc(taskId, PageRequest.of(0, limit));
    }

    public List<ScheduleRunLogEntity> listRecent(int limit) {
        return repository.findTop20ByOrderByStartedAtDesc();
    }
}
```

- [ ] **Step 4: 运行测试，确认 PASS**

```bash
mvn test -pl . -Dtest=ScheduleRunLogServiceTest -q 2>&1 | tail -10
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V26__schedule_run_logs.sql \
        src/main/resources/db/migration-sqlite/V26__schedule_run_logs.sql \
        src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogEntity.java \
        src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogRepository.java \
        src/main/java/com/jaguarliu/ai/schedule/ScheduleRunLogService.java \
        src/test/java/com/jaguarliu/ai/schedule/ScheduleRunLogServiceTest.java
git commit -m "feat(schedule): add ScheduleRunLog entity, repository, and service"
```

---

## Chunk 2: 后端 — 接入执行器 + AuditLog + RPC

### Task 4: 修改 ScheduledTaskExecutor — 写入执行记录

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/schedule/ScheduledTaskExecutor.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/AuditLogService.java`
- Test: `src/test/java/com/jaguarliu/ai/schedule/ScheduledTaskExecutorTest.java`（查看现有测试，确认不破坏）

- [ ] **Step 1: 给 AuditLogService 新增 logScheduleExecution 方法**

在 `AuditLogService.java` 末尾、`truncate` 方法之前添加：

```java
/**
 * 记录定时任务执行事件
 * eventType = "SCHEDULE_EXECUTION"
 */
public void logScheduleExecution(String taskId, String taskName, String triggeredBy,
                                  String sessionId, String runId,
                                  String resultStatus, String resultSummary, long durationMs) {
    AuditLogEntity entity = AuditLogEntity.builder()
            .eventType("SCHEDULE_EXECUTION")
            .sessionId(sessionId)
            .runId(runId)
            .toolName(taskName)
            .command(triggeredBy)            // 复用 command 字段存 triggeredBy
            .resultStatus(resultStatus)
            .resultSummary(truncate(resultSummary))
            .durationMs((int) durationMs)
            .hitlRequired(false)
            .build();

    try {
        auditLogRepository.save(entity);
        log.debug("Schedule audit log recorded: task={}, status={}", taskName, resultStatus);
    } catch (Exception e) {
        log.error("Failed to record schedule audit log: task={}", taskName, e);
    }
}
```

- [ ] **Step 2: 修改 ScheduledTaskExecutor — 注入 ScheduleRunLogService 和 AuditLogService，改造 execute()**

将 `ScheduledTaskExecutor` 依赖声明区改为：

```java
private final SessionService sessionService;
private final RunService runService;
private final MessageService messageService;
private final AgentRuntime agentRuntime;
private final ContextBuilder contextBuilder;
private final DeliveryToolService deliveryToolService;
private final CancellationManager cancellationManager;
private final LoopConfig loopConfig;
private final ScheduledTaskRepository repository;
private final ObjectMapper objectMapper;
private final ScheduleRunLogService runLogService;
private final AuditLogService auditLogService;
```

将 `execute()` 方法改为（注意第二个参数 triggeredBy 新增）：

```java
public void execute(ScheduledTaskEntity task) {
    execute(task, "scheduled");
}

public void execute(ScheduledTaskEntity task, String triggeredBy) {
    log.info("Scheduled task executing: name={}, id={}, triggeredBy={}", task.getName(), task.getId(), triggeredBy);
    long startMs = System.currentTimeMillis();

    // 立即写入 RUNNING 记录
    ScheduleRunLogEntity runLog = runLogService.start(task.getId(), task.getName(), triggeredBy, null, null);

    try {
        String executionPrompt = buildExecutionPrompt(task);

        // 1. 创建专用会话
        SessionEntity session = sessionService.createScheduledSession("[Scheduled] " + task.getName());

        // 2. 创建 run
        RunEntity run = runService.create(session.getId(), task.getPrompt());
        runService.updateStatus(run.getId(), RunStatus.RUNNING);

        // 3. 保存用户消息
        messageService.saveUserMessage(session.getId(), run.getId(), task.getPrompt());

        // 4. 构建消息上下文
        List<LlmRequest.Message> messages = contextBuilder.buildMessages(List.of(), executionPrompt);

        // 5. 构建 RunContext
        RunContext context = RunContext.createScheduled(
                run.getId(), session.getId(), loopConfig, cancellationManager);
        context.setAgentDeniedTools(resolveAutoDeliveryDeniedTools(task));

        // 6. 执行 Agent 循环
        String response = agentRuntime.executeLoopWithContext(context, messages, executionPrompt);

        // 7. 保存助手消息
        messageService.saveAssistantMessage(session.getId(), run.getId(), response);
        runService.updateStatus(run.getId(), RunStatus.DONE);

        // 8. 推送结果到目标
        pushToDeliveryTarget(task, response, true, null);

        // 9. 更新任务状态
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunSuccess(true);
        task.setLastRunError(null);
        repository.save(task);

        // 10. 完成执行记录
        runLogService.complete(runLog, session.getId(), run.getId());

        // 11. 写入 AuditLog
        long durationMs = System.currentTimeMillis() - startMs;
        auditLogService.logScheduleExecution(
                task.getId(), task.getName(), triggeredBy,
                session.getId(), run.getId(),
                "SUCCESS", "Task completed successfully", durationMs);

        log.info("Scheduled task completed: name={}, id={}", task.getName(), task.getId());

    } catch (Exception e) {
        log.error("Scheduled task failed: name={}, error={}", task.getName(), e.getMessage(), e);

        // 推送错误到目标
        pushToDeliveryTarget(task, null, false, e.getMessage());

        // 更新任务状态
        task.setLastRunAt(LocalDateTime.now());
        task.setLastRunSuccess(false);
        task.setLastRunError(e.getMessage());
        repository.save(task);

        // 标记执行记录失败
        runLogService.fail(runLog, e.getMessage());

        // 写入 AuditLog
        long durationMs = System.currentTimeMillis() - startMs;
        auditLogService.logScheduleExecution(
                task.getId(), task.getName(), triggeredBy,
                null, null,
                "FAILED", e.getMessage(), durationMs);
    }
}
```

- [ ] **Step 3: 修改 ScheduledTaskService.runNow() — 传递 triggeredBy = "manual"**

```java
public void runNow(String id) {
    ScheduledTaskEntity task = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Scheduled task not found: " + id));
    new Thread(() -> executor.execute(task, "manual"), "schedule-manual-" + task.getName()).start();
    log.info("Manual run triggered for scheduled task: name={}", task.getName());
}
```

- [ ] **Step 4: 运行现有的 ScheduledTaskExecutorTest，确认不破坏**

```bash
mvn test -pl . -Dtest=ScheduledTaskExecutorTest -q 2>&1 | tail -20
```

若测试因缺少 mock 失败，在测试中补充 `runLogService` 和 `auditLogService` 的 mock 注入。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/schedule/ScheduledTaskExecutor.java \
        src/main/java/com/jaguarliu/ai/schedule/ScheduledTaskService.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/AuditLogService.java \
        src/test/java/com/jaguarliu/ai/schedule/ScheduledTaskExecutorTest.java
git commit -m "feat(schedule): record run history and audit log on each execution"
```

---

### Task 5: 新增 RPC Handler — schedule.runs.list

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/schedule/ScheduleRunsListHandler.java`

- [ ] **Step 1: 实现 Handler**

```java
// src/main/java/com/jaguarliu/ai/gateway/rpc/handler/schedule/ScheduleRunsListHandler.java
package com.jaguarliu.ai.gateway.rpc.handler.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.schedule.ScheduleRunLogEntity;
import com.jaguarliu.ai.schedule.ScheduleRunLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schedule.runs.list — 查询某个任务的执行历史
 * params: { taskId?: string, limit?: number }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleRunsListHandler implements RpcHandler {

    private final ScheduleRunLogService runLogService;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "schedule.runs.list";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String taskId = params != null ? (String) params.get("taskId") : null;
            int limit = params != null && params.get("limit") instanceof Number n
                    ? Math.min(n.intValue(), 100) : 20;

            List<ScheduleRunLogEntity> logs = taskId != null && !taskId.isBlank()
                    ? runLogService.listByTask(taskId, limit)
                    : runLogService.listRecent(limit);

            var dtos = logs.stream().map(ScheduleRunsListHandler::toDto).toList();
            return RpcResponse.success(request.getId(), dtos);
        }).onErrorResume(e -> {
            log.error("Failed to list schedule runs: {}", e.getMessage());
            return Mono.just(RpcResponse.error(request.getId(), "LIST_FAILED", e.getMessage()));
        });
    }

    private static Map<String, Object> toDto(ScheduleRunLogEntity e) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", e.getId());
        dto.put("taskId", e.getTaskId());
        dto.put("taskName", e.getTaskName());
        dto.put("triggeredBy", e.getTriggeredBy());
        dto.put("status", e.getStatus());
        dto.put("startedAt", e.getStartedAt() != null ? e.getStartedAt().toString() : null);
        dto.put("finishedAt", e.getFinishedAt() != null ? e.getFinishedAt().toString() : null);
        dto.put("durationMs", e.getDurationMs());
        dto.put("errorMessage", e.getErrorMessage());
        dto.put("sessionId", e.getSessionId());
        dto.put("runId", e.getRunId());
        return dto;
    }
}
```

- [ ] **Step 2: 运行编译确认**

```bash
mvn compile -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/schedule/ScheduleRunsListHandler.java
git commit -m "feat(schedule): add schedule.runs.list RPC handler"
```

---

## Chunk 3: 前端 — 类型 + Composable + UI

### Task 6: 新增 ScheduleRunLog 类型 + 更新 useSchedules

**Files:**
- Modify: `jaguarclaw-ui/src/types/index.ts`
- Modify: `jaguarclaw-ui/src/composables/useSchedules.ts`

- [ ] **Step 1: 在 types/index.ts 的 Schedule Types 区域末尾追加**

在 `ScheduleUpdatePayload` 定义之后添加：

```typescript
export interface ScheduleRunLog {
  id: string
  taskId: string
  taskName: string
  triggeredBy: 'scheduled' | 'manual'
  status: 'running' | 'success' | 'failed'
  startedAt: string
  finishedAt: string | null
  durationMs: number | null
  errorMessage: string | null
  sessionId: string | null
  runId: string | null
}
```

- [ ] **Step 2: 更新 useSchedules.ts — 增加 runHistory 状态和 loadRunHistory**

在现有 composable 内，在模块级 ref 区域（`schedules`, `loading`, `error` 下方）添加：

```typescript
const runHistory = ref<Map<string, ScheduleRunLog[]>>(new Map())
const runHistoryLoading = ref<Set<string>>(new Set())
```

然后新增 `loadRunHistory` 函数（在 `runSchedule` 之后）：

```typescript
async function loadRunHistory(taskId: string): Promise<void> {
  runHistoryLoading.value.add(taskId)
  try {
    const result = await request<ScheduleRunLog[]>('schedule.runs.list', { taskId, limit: 10 })
    runHistory.value = new Map(runHistory.value).set(taskId, result)
  } catch (e) {
    console.error('[Schedules] Failed to load run history:', e)
  } finally {
    runHistoryLoading.value.delete(taskId)
  }
}
```

修改 `runSchedule` — 触发后立即加载历史：

```typescript
async function runSchedule(id: string): Promise<void> {
  error.value = null
  try {
    await request<{ success: boolean }>('schedule.run', { id })
    // 立即加载历史（会看到 RUNNING 记录）
    await loadRunHistory(id)
    await loadSchedules()
  } catch (e) {
    console.error('[Schedules] Failed to run schedule:', e)
    error.value = e instanceof Error ? e.message : 'Failed to run schedule'
    throw e
  }
}
```

更新 return 对象：

```typescript
return {
  schedules: readonly(schedules),
  loading: readonly(loading),
  error: readonly(error),
  runHistory: readonly(runHistory),
  runHistoryLoading: readonly(runHistoryLoading),
  loadSchedules,
  createSchedule,
  updateSchedule,
  removeSchedule,
  toggleSchedule,
  runSchedule,
  loadRunHistory
}
```

另外，确保 `ScheduleRunLog` 已加入 import：

```typescript
import type { ScheduleInfo, ScheduleCreatePayload, ScheduleUpdatePayload, ScheduleRunLog } from '@/types'
```

- [ ] **Step 3: 确认 TypeScript 编译无错误**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npx vue-tsc --noEmit 2>&1 | head -30
```

Expected: 无错误输出

- [ ] **Step 4: Commit**

```bash
git add jaguarclaw-ui/src/types/index.ts \
        jaguarclaw-ui/src/composables/useSchedules.ts
git commit -m "feat(schedule): add ScheduleRunLog type and loadRunHistory composable"
```

---

### Task 7: 更新 SchedulesSection.vue — 显示执行历史

**Files:**
- Modify: `jaguarclaw-ui/src/components/settings/SchedulesSection.vue`

目标效果：
- 每个任务卡片底部可展开"执行历史"区域（点击任务卡片或"历史"按钮展开）
- 展开后发起 `loadRunHistory(task.id)` 并显示最近 10 条
- 每条显示：状态图标、触发方式、开始时间、耗时/错误信息
- "立即执行"按钮触发后，历史区域自动展开并显示 RUNNING 状态

- [ ] **Step 1: 在 `<script setup>` 中更新 composable 解构和状态**

将 `useSchedules()` 解构改为：

```typescript
const {
  schedules, loading, error,
  runHistory, runHistoryLoading,
  loadSchedules, createSchedule, updateSchedule, removeSchedule,
  toggleSchedule, runSchedule, loadRunHistory
} = useSchedules()
```

新增展开状态：

```typescript
const expandedHistory = ref<Set<string>>(new Set())

function toggleHistory(taskId: string) {
  const next = new Set(expandedHistory.value)
  if (next.has(taskId)) {
    next.delete(taskId)
  } else {
    next.add(taskId)
    loadRunHistory(taskId)
  }
  expandedHistory.value = next
}
```

修改 `handleRun` — 执行后自动展开历史：

```typescript
async function handleRun(taskId: string) {
  runningTasks.value.add(taskId)
  // 执行时自动展开历史
  expandedHistory.value = new Set(expandedHistory.value).add(taskId)
  try {
    await runSchedule(taskId)   // runSchedule 内部已调用 loadRunHistory
  } catch {
    // Error handled in composable
  } finally {
    runningTasks.value.delete(taskId)
  }
}
```

新增辅助函数：

```typescript
function formatDuration(ms: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function getRunStatusIcon(status: string): string {
  if (status === 'running') return '⟳'
  if (status === 'success') return '✓'
  return '✗'
}

function getRunStatusClass(status: string): string {
  if (status === 'running') return 'run-status-running'
  if (status === 'success') return 'run-status-success'
  return 'run-status-failed'
}
```

- [ ] **Step 2: 在 task-card 模板中添加历史按钮和历史区域**

在 `.task-actions` 区域内，`edit-btn` 之前添加：

```html
<button
  class="history-btn"
  :class="{ active: expandedHistory.has(task.id) }"
  @click="toggleHistory(task.id)"
>
  {{ t('sections.schedules.historyBtn') }}
</button>
```

在 `.task-card` 的末尾（`.task-error` 之后）添加：

```html
<!-- 执行历史 -->
<div v-if="expandedHistory.has(task.id)" class="run-history">
  <div v-if="runHistoryLoading.has(task.id)" class="run-history-loading">
    {{ t('sections.schedules.historyLoading') }}
  </div>
  <template v-else>
    <div v-if="!runHistory.get(task.id)?.length" class="run-history-empty">
      {{ t('sections.schedules.historyEmpty') }}
    </div>
    <div
      v-for="run in runHistory.get(task.id)"
      :key="run.id"
      class="run-entry"
      :class="getRunStatusClass(run.status)"
    >
      <span class="run-status-icon">{{ getRunStatusIcon(run.status) }}</span>
      <span class="run-trigger">{{ run.triggeredBy }}</span>
      <span class="run-time">{{ formatTime(run.startedAt) }}</span>
      <span class="run-duration">{{ formatDuration(run.durationMs) }}</span>
      <span v-if="run.errorMessage" class="run-error-msg" :title="run.errorMessage">
        {{ run.errorMessage.length > 60 ? run.errorMessage.substring(0, 60) + '...' : run.errorMessage }}
      </span>
    </div>
  </template>
</div>
```

- [ ] **Step 3: 在 `<style scoped>` 末尾添加样式**

```css
/* Run History */
.history-btn {
  padding: 4px 10px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
}

.history-btn:hover {
  background: var(--color-gray-bg);
}

.history-btn.active {
  background: var(--color-gray-bg);
  border-color: var(--color-gray-dark);
}

.run-history {
  margin-top: 10px;
  padding-top: 10px;
  border-top: var(--border);
}

.run-history-loading,
.run-history-empty {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-gray-dark);
  padding: 4px 0;
}

.run-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-family: var(--font-mono);
  font-size: 12px;
  border-bottom: 1px solid var(--color-gray-100, #f3f4f6);
}

.run-entry:last-child {
  border-bottom: none;
}

.run-status-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
}

.run-status-running .run-status-icon { color: #f59e0b; }
.run-status-success .run-status-icon { color: #22c55e; }
.run-status-failed  .run-status-icon { color: #ef4444; }

.run-trigger {
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  background: #f3f4f6;
  color: #6b7280;
}

.run-time {
  color: var(--color-gray-dark);
  flex: 1;
}

.run-duration {
  color: var(--color-gray-dark);
  min-width: 50px;
  text-align: right;
}

.run-error-msg {
  color: #ef4444;
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

- [ ] **Step 4: 新增 i18n key**

在 `jaguarclaw-ui/src/i18n/locales/zh.ts` 的 `sections.schedules` 对象中添加：

```typescript
historyBtn: '历史',
historyLoading: '加载中...',
historyEmpty: '暂无执行记录',
```

在 `jaguarclaw-ui/src/i18n/locales/en.ts` 的同位置添加：

```typescript
historyBtn: 'History',
historyLoading: 'Loading...',
historyEmpty: 'No execution history',
```

- [ ] **Step 5: 确认 TypeScript 编译无错误**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npx vue-tsc --noEmit 2>&1 | head -30
```

- [ ] **Step 6: Commit**

```bash
git add jaguarclaw-ui/src/components/settings/SchedulesSection.vue \
        jaguarclaw-ui/src/i18n/locales/zh.ts \
        jaguarclaw-ui/src/i18n/locales/en.ts
git commit -m "feat(schedule): show run history in schedule card with inline expand"
```

---

## Chunk 4: 验收

### Task 8: 集成验证

- [ ] **Step 1: 启动后端，确认 Flyway 迁移成功**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn spring-boot:run 2>&1 | grep -E "V26|schedule_run_logs|Flyway"
```

Expected: `Successfully applied 1 migration to schema ... (V26__schedule_run_logs)`

- [ ] **Step 2: 触发一个定时任务的"立即执行"**

通过 WebSocket RPC 发送（或在 UI 点击）：
```json
{ "method": "schedule.run", "payload": { "id": "<task-id>" } }
```

Expected: 返回 `{ "success": true }`

- [ ] **Step 3: 查询执行历史确认记录存在**

```json
{ "method": "schedule.runs.list", "payload": { "taskId": "<task-id>" } }
```

Expected: 返回包含一条 `status: "running"` 或 `status: "success"` 的记录

- [ ] **Step 4: 查询 AuditLog 确认 SCHEDULE_EXECUTION 事件存在**

```json
{ "method": "audit.logs.list", "payload": { "eventType": "SCHEDULE_EXECUTION" } }
```

Expected: 有对应记录

- [ ] **Step 5: UI 端验证**

1. 打开 Settings > Schedules
2. 点击某任务的"历史"按钮 → 展开，显示历史（或"暂无执行记录"）
3. 点击"立即执行" → 历史区域自动展开，显示 RUNNING 记录
4. 等执行完成后再次点击历史 → 显示 SUCCESS 记录

- [ ] **Step 6: 运行全部 Schedule 相关测试**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -pl . -Dtest="ScheduleRunLogServiceTest,ScheduledTaskExecutorTest,ScheduledTaskServiceTest,ScheduleUpdateHandlerTest" -q 2>&1 | tail -10
```

Expected: 全部 PASS
