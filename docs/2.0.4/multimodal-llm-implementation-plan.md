# Multimodal LLM Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Upgrade the current text-only LLM integration into a structured multimodal input framework, with image understanding as the first supported capability.

**Architecture:** Introduce structured message parts in `LlmRequest`, pass image attachments through `agent.run`, persist multimodal message payloads for history replay, and implement OpenAI-compatible image input as the first provider path. Keep the current `LlmClient` interface stable while moving request/response adaptation toward provider-specific codec logic.

**Tech Stack:** Spring Boot, Java, WebFlux `WebClient`, Vue 3, TypeScript, Electron desktop upload flow, SQLite/JPA message persistence.

---

### Task 1: Add structured multimodal message model

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmRequest.java`
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmResponse.java` (only if needed for future symmetry)
- Test: new model-focused tests under `src/test/java/com/jaguarliu/ai/llm/`

**Steps:**
1. Add `parts` to `LlmRequest.Message`.
2. Add `ContentPart` and `ImagePart` value objects.
3. Keep `content` as a backward-compatible shortcut.
4. Add helper constructors for text-only and text+image messages.
5. Add tests covering legacy-to-parts fallback behavior.

### Task 2: Upgrade agent.run payload to carry attachments

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/agent/AgentRunHandler.java`
- Modify: frontend sender in `jaguarclaw-ui/src/composables/useChat.ts`
- Modify: related UI types in `jaguarclaw-ui/src/types/index.ts`
- Test: handler tests under `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/agent/`

**Steps:**
1. Extend `agent.run` payload parsing to read `attachments`.
2. Build `LlmRequest.Message.parts` from `prompt + attachments`.
3. Stop converting image attachments into prompt text.
4. Keep existing non-image contexts on the prompt/context side for now.
5. Add regression tests for text-only and text+image requests.

### Task 3: Extend upload pipeline for images

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/gateway/ws/FileUploadRouter.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/SessionFileService.java`
- Modify: `src/main/java/com/jaguarliu/ai/storage/entity/SessionFileEntity.java`
- Modify: frontend upload helpers if needed
- Test: upload route / session file tests

**Steps:**
1. Allow common image extensions in upload validation.
2. Return `mimeType` in upload response.
3. Persist image metadata in `SessionFileEntity`.
4. Keep files stored as workspace-relative references.
5. Add tests for accepted image upload metadata.

### Task 4: Persist multimodal message history

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/storage/entity/MessageEntity.java`
- Modify: `src/main/java/com/jaguarliu/ai/session/MessageService.java`
- Modify: DB migration/init scripts if applicable
- Test: message persistence tests

**Steps:**
1. Add a structured JSON field for multimodal message payload.
2. Save new multimodal user messages with both summary text and structured JSON.
3. Update `toRequestMessages()` to rebuild from JSON when present.
4. Keep old rows readable via text fallback.
5. Add tests covering mixed legacy/new history replay.

### Task 5: Add OpenAI-compatible image input support

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java`
- Create if needed: provider codec/helper classes under `src/main/java/com/jaguarliu/ai/llm/`
- Test: `src/test/java/com/jaguarliu/ai/llm/`

**Steps:**
1. Convert structured message parts into OpenAI-compatible `content[]` arrays.
2. Load workspace image files and encode them as provider input.
3. Preserve current text/tool-calling behavior for legacy requests.
4. Keep streaming response parsing unchanged for MVP.
5. Add tests for text-only and text+image request serialization.

### Task 6: Add provider/model capability checks

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmProviderConfig.java`
- Modify: `src/main/java/com/jaguarliu/ai/llm/LlmProperties.java`
- Modify: LLM settings UI in `jaguarclaw-ui/src/components/settings/LlmSection.vue`
- Test: provider config tests + UI type-check/build

**Steps:**
1. Introduce minimal capability metadata or a resolver for `supportsVision`.
2. Reject image requests when the selected model/provider lacks vision capability.
3. Surface capability information to the frontend model selector.
4. Show a clear UI message when a non-vision model is selected.
5. Add tests for capability-based acceptance/rejection.

### Task 7: Deliver desktop image-understanding MVP

**Files:**
- Verify end-to-end touched files above
- Optional docs: `docs/2.0.4/` follow-up notes

**Steps:**
1. Verify text-only requests still work unchanged.
2. Verify image attachment requests are transmitted as multimodal input.
3. Verify follow-up turns can replay image history.
4. Verify unsupported models fail clearly.
5. Capture final implementation notes and limitations.
