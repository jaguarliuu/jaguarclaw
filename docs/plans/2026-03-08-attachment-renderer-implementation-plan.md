# Attachment Renderer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Render chat attachments by file type so images display inline, text files show richer previews, and unsupported files degrade gracefully to a generic card.

**Architecture:** Introduce a small attachment normalization layer in the UI, then route both user-attached files and agent-generated files through a shared attachment renderer. Keep input-area chips unchanged; only upgrade message-area rendering so the change is low risk and easy to validate.

**Tech Stack:** Vue 3, TypeScript, existing workspace file endpoint (`/api/workspace/**`), existing artifact side panel.

---

### Task 1: Add attachment normalization utilities

**Files:**
- Create: `jaguarclaw-ui/src/utils/attachments.ts`
- Modify: `jaguarclaw-ui/src/types/index.ts` (only if new UI-only type export is needed)

**Step 1: Write the failing test**
- No frontend test harness exists in `jaguarclaw-ui/package.json`; do not introduce a new framework in this task.

**Step 2: Validate baseline manually**
- Confirm current behavior: images and text files both render as generic horizontal cards/chips.

**Step 3: Write minimal implementation**
- Add helpers for file extension, type inference, workspace URL generation, and normalization from `SessionFile` / `AttachedContext` into one attachment view model.

**Step 4: Verify utility usage via build**
- Run: `npm --prefix jaguarclaw-ui run build`
- Expected: build passes.

### Task 2: Build shared attachment renderer

**Files:**
- Create: `jaguarclaw-ui/src/components/attachments/AttachmentCard.vue`

**Step 1: Write the failing test**
- No frontend test harness exists; validate visually after wiring.

**Step 2: Write minimal implementation**
- Render three variants in one shared card:
  - image: inline thumbnail + file meta + actions
  - text: title + file meta + short fetched excerpt + preview/download actions
  - generic: stronger file card with type badge + actions
- Reuse existing artifact panel for text preview; open image via workspace URL.

**Step 3: Run build**
- Run: `npm --prefix jaguarclaw-ui run build`
- Expected: build passes.

### Task 3: Wrap agent files with shared renderer

**Files:**
- Modify: `jaguarclaw-ui/src/components/FileCard.vue`

**Step 1: Implement adapter**
- Convert `SessionFile` props into normalized attachment data and forward to `AttachmentCard`.

**Step 2: Run build**
- Run: `npm --prefix jaguarclaw-ui run build`
- Expected: build passes.

### Task 4: Upgrade user message file attachments

**Files:**
- Create: `jaguarclaw-ui/src/components/ContextFileCard.vue`
- Modify: `jaguarclaw-ui/src/components/MessageItem.vue`

**Step 1: Implement adapter**
- Render `AttachedContext` entries with `type === 'file'` through the shared card.
- Keep non-file contexts on existing `ContextChip` path.

**Step 2: Verify user-message layout**
- Ensure cards align cleanly in user messages and do not break markdown content spacing.

**Step 3: Run build**
- Run: `npm --prefix jaguarclaw-ui run build`
- Expected: build passes.

### Task 5: Polish and manual smoke-check

**Files:**
- Modify: any touched UI styles only if spacing or alignment needs correction

**Step 1: Manual smoke checks**
- Upload an image and send it: thumbnail should appear in the message.
- Upload a text/markdown/json file and send it: excerpt card should appear.
- Generate or display an agent file block: file card should use the same visual language.
- Unknown/binary file should still render as a safe generic card.

**Step 2: Final verification**
- Run: `npm --prefix jaguarclaw-ui run build`
- Expected: build passes with only existing bundle-size warnings.
