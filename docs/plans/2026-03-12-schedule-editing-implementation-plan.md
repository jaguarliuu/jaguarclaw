# Schedule Editing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add manual editing for existing scheduled tasks so users can update AI-created or manually created schedules without deleting and recreating them.

**Architecture:** Extend the existing schedule RPC surface with a new `schedule.update` method, implement in-place updates inside `ScheduledTaskService`, and reuse the existing settings form in the frontend for edit mode. Keep runtime status fields read-only and preserve schedule identity and recent execution state during edits.

**Tech Stack:** Spring Boot, JPA, Reactor, Vue 3, TypeScript

---

### Task 1: Add failing backend service tests for schedule updates

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/schedule/ScheduledTaskServiceTest.java`
- Modify: `src/main/java/com/jaguarliu/ai/schedule/ScheduledTaskService.java`
- Test: `src/test/java/com/jaguarliu/ai/schedule/ScheduledTaskServiceTest.java`

**Step 1: Write the failing test**

Cover:

- update persists editable fields
- update preserves last run fields
- update disabled task cancels without rescheduling
- update missing task throws

**Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScheduledTaskServiceTest test`

Expected: FAIL because `ScheduledTaskService` has no `update(...)` method yet.

**Step 3: Write minimal implementation**

Add `update(...)` to `ScheduledTaskService`, refactor shared validation, cancel existing future, save entity, and reschedule only when enabled.

**Step 4: Run test to verify it passes**

Run: `./mvnw -Dtest=ScheduledTaskServiceTest test`

Expected: PASS

### Task 2: Add failing RPC handler and authorization tests

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/schedule/ScheduleUpdateHandlerTest.java`
- Modify: `src/test/java/com/jaguarliu/ai/gateway/security/RpcAuthorizationServiceTest.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/security/RpcAuthorizationService.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/schedule/ScheduleUpdateHandler.java`

**Step 1: Write the failing test**

Cover:

- `schedule.update` success returns latest DTO
- missing required params return `INVALID_PARAMS`
- authorization resolves `schedule.update` to WRITE

**Step 2: Run test to verify it fails**

Run: `./mvnw -Dtest=ScheduleUpdateHandlerTest,RpcAuthorizationServiceTest test`

Expected: FAIL because handler and authorization entry do not exist yet.

**Step 3: Write minimal implementation**

Add `ScheduleUpdateHandler`, wire validation, and register `schedule.update` in WRITE methods.

**Step 4: Run test to verify it passes**

Run: `./mvnw -Dtest=ScheduleUpdateHandlerTest,RpcAuthorizationServiceTest test`

Expected: PASS

### Task 3: Add frontend edit-mode support

**Files:**
- Modify: `jaguarclaw-ui/src/types/index.ts`
- Modify: `jaguarclaw-ui/src/composables/useSchedules.ts`
- Modify: `jaguarclaw-ui/src/components/settings/SchedulesSection.vue`
- Modify: `jaguarclaw-ui/src/i18n/locales/zh.ts`
- Modify: `jaguarclaw-ui/src/i18n/locales/en.ts`

**Step 1: Write the failing behavior definition**

Use the approved design as the contract:

- list cards expose edit action
- edit action preloads the existing form
- submit calls `schedule.update` instead of `schedule.create`

**Step 2: Run targeted type/build verification**

Run: `cd jaguarclaw-ui && npm run build`

Expected: FAIL until new payload types and component state are wired.

**Step 3: Write minimal implementation**

Add `ScheduleUpdatePayload`, `updateSchedule()` in the composable, edit-mode state in the component, and localized UI copy for edit/save/cancel.

**Step 4: Run build verification**

Run: `cd jaguarclaw-ui && npm run build`

Expected: PASS

### Task 4: Verify end-to-end behavior at unit/build level

**Files:**
- No code changes required unless verification fails

**Step 1: Run backend targeted tests**

Run: `./mvnw -Dtest=ScheduledTaskServiceTest,ScheduleUpdateHandlerTest,RpcAuthorizationServiceTest test`

Expected: PASS

**Step 2: Run frontend build**

Run: `cd jaguarclaw-ui && npm run build`

Expected: PASS

**Step 3: Optional broader regression check**

Run: `./mvnw test -DskipITs`

Expected: PASS if time permits; otherwise document as not run.
