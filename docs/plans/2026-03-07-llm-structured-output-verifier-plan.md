# LLM Structured Output and Verifier Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a reusable structured-output capability to the LLM layer and refactor runtime verification to use a hybrid rule-based + LLM-based verifier pipeline.

**Architecture:** First add a provider-agnostic structured-output contract to `LlmRequest` / `LlmClient`, then implement native-schema-or-prompt-fallback in the OpenAI-compatible client. After that, introduce a structured verifier decision model and compose a hybrid verifier that preserves cheap high-confidence rules while moving open-world outcome judgments to an LLM call.

**Tech Stack:** Java 17, Spring Boot, Jackson, JUnit 5, Mockito, Maven.

---

### Task 1: Add structured output request and result models

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/model/LlmRequest.java`
- Create: `src/main/java/com/jaguarliu/ai/llm/model/StructuredOutputSpec.java`
- Create: `src/main/java/com/jaguarliu/ai/llm/model/StructuredLlmResult.java`
- Test: `src/test/java/com/jaguarliu/ai/llm/model/LlmRequestStructuredOutputTest.java`

**Step 1: Write the failing test**
- Verify `LlmRequest` can carry an optional structured output spec.
- Verify `StructuredLlmResult` can represent parsed object + raw text + fallback metadata.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=LlmRequestStructuredOutputTest test`
Expected: FAIL because structured output model types do not exist.

**Step 3: Write minimal implementation**
- Add `structuredOutput` to `LlmRequest`.
- Add `StructuredOutputSpec` with `name`, `jsonSchema`, `strict`, `fallbackToPromptJson`.
- Add `StructuredLlmResult<T>` with `value`, `rawText`, `nativeStructuredOutput`, `fallbackUsed`.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=LlmRequestStructuredOutputTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/llm/model/LlmRequest.java \
        src/main/java/com/jaguarliu/ai/llm/model/StructuredOutputSpec.java \
        src/main/java/com/jaguarliu/ai/llm/model/StructuredLlmResult.java \
        src/test/java/com/jaguarliu/ai/llm/model/LlmRequestStructuredOutputTest.java
git commit -m "feat(llm): add structured output request models"
```

### Task 2: Add structured output parser service

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/llm/StructuredOutputService.java`
- Create: `src/main/java/com/jaguarliu/ai/llm/StructuredOutputException.java`
- Test: `src/test/java/com/jaguarliu/ai/llm/StructuredOutputServiceTest.java`

**Step 1: Write the failing tests**
- Parse plain JSON into typed object.
- Parse fenced code block JSON.
- Fail on invalid JSON with explicit exception.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=StructuredOutputServiceTest test`
Expected: FAIL because service types do not exist.

**Step 3: Write minimal implementation**
- Add `extractJsonPayload(String raw)`.
- Parse with Jackson into target type.
- Throw `StructuredOutputException` on invalid or empty payload.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=StructuredOutputServiceTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/llm/StructuredOutputService.java \
        src/main/java/com/jaguarliu/ai/llm/StructuredOutputException.java \
        src/test/java/com/jaguarliu/ai/llm/StructuredOutputServiceTest.java
git commit -m "feat(llm): add structured output parser service"
```

### Task 3: Extend LLM client contract with structured output execution

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/llm/LlmClient.java`
- Modify: `src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java`
- Test: `src/test/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClientStructuredOutputTest.java`

**Step 1: Write the failing tests**
- Verify a request with `StructuredOutputSpec` is serialized into provider request payload.
- Verify fallback prompt-json path is used when native schema output is not enabled.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=OpenAiCompatibleLlmClientStructuredOutputTest test`
Expected: FAIL because client lacks structured output support.

**Step 3: Write minimal implementation**
- Add `structured(...)` method to `LlmClient`.
- In `OpenAiCompatibleLlmClient`, prefer native schema request fields when configured.
- Fallback to a prompt-enforced JSON-only call and parse via `StructuredOutputService`.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=OpenAiCompatibleLlmClientStructuredOutputTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/llm/LlmClient.java \
        src/main/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClient.java \
        src/test/java/com/jaguarliu/ai/llm/OpenAiCompatibleLlmClientStructuredOutputTest.java
git commit -m "feat(llm): add structured output execution"
```

### Task 4: Introduce verifier decision schema and LLM-based verifier

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/VerifierDecision.java`
- Create: `src/main/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStage.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStageTest.java`

**Step 1: Write the failing tests**
- Verify a structured LLM decision maps to `BLOCKED_BY_ENVIRONMENT`.
- Verify a structured LLM decision maps to `BLOCKED_PENDING_USER_DECISION`.
- Verify parse or provider failure degrades to non-terminal fallback instead of crashing runtime.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=LlmRuntimeDecisionStageTest test`
Expected: FAIL because verifier decision types do not exist.

**Step 3: Write minimal implementation**
- Define `VerifierDecision` schema fields: `terminal`, `shouldContinue`, `outcome`, `failureCategory`, `reason`, `userMessage`, `confidence`.
- Implement `LlmRuntimeDecisionStage` using `LlmClient.structured(...)` and map to `VerificationResult`.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=LlmRuntimeDecisionStageTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/runtime/VerifierDecision.java \
        src/main/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStage.java \
        src/test/java/com/jaguarliu/ai/runtime/LlmRuntimeDecisionStageTest.java
git commit -m "feat(runtime): add llm-based verifier"
```

### Task 5: Convert current default verifier into cheap rule fallback

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/DefaultRuntimeDecisionStage.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/ToolExecutor.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/DefaultRuntimeDecisionStageTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/ToolExecutorTest.java`

**Step 1: Write the failing tests**
- Add Windows/Chinese environment-error coverage:
  - `不是内部或外部命令`
  - `系统找不到指定的文件`
  - `File not found`
- Verify assistant reply alone does not automatically imply `COMPLETED`.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=DefaultRuntimeDecisionStageTest,ToolExecutorTest test`
Expected: FAIL because these signals are not recognized.

**Step 3: Write minimal implementation**
- Keep only high-confidence shortcut rules.
- Remove `assistantReply != blank => completed` behavior.
- Extend failure categorization for Windows/localized blocker messages.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=DefaultRuntimeDecisionStageTest,ToolExecutorTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/runtime/DefaultRuntimeDecisionStage.java \
        src/main/java/com/jaguarliu/ai/runtime/ToolExecutor.java \
        src/test/java/com/jaguarliu/ai/runtime/DefaultRuntimeDecisionStageTest.java \
        src/test/java/com/jaguarliu/ai/runtime/ToolExecutorTest.java
git commit -m "refactor(runtime): narrow rule-based verifier"
```

### Task 6: Compose hybrid verifier and wire into runtime

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/runtime/CompositeRuntimeDecisionStage.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: Spring wiring if needed in runtime configuration
- Test: `src/test/java/com/jaguarliu/ai/runtime/CompositeRuntimeDecisionStageTest.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/AgentRuntimeTest.java`

**Step 1: Write the failing tests**
- Verify rule-based terminal results short-circuit LLM verifier.
- Verify LLM verifier is used when rules do not resolve a terminal outcome.
- Verify the `wkhtmltopdf + README.md missing` scenario surfaces `BLOCKED_BY_ENVIRONMENT` instead of silently ending.

**Step 2: Run test to verify it fails**
Run: `mvn -Dtest=CompositeRuntimeDecisionStageTest,AgentRuntimeTest test`
Expected: FAIL because composite pipeline does not exist.

**Step 3: Write minimal implementation**
- Implement `CompositeRuntimeDecisionStage`.
- Prefer rule-based shortcuts, then LLM verifier, then safe continue fallback.
- Wire runtime to use the composite verifier by default.

**Step 4: Run test to verify it passes**
Run: `mvn -Dtest=CompositeRuntimeDecisionStageTest,AgentRuntimeTest test`
Expected: PASS.

**Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/runtime/CompositeRuntimeDecisionStage.java \
        src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java \
        src/test/java/com/jaguarliu/ai/runtime/CompositeRuntimeDecisionStageTest.java \
        src/test/java/com/jaguarliu/ai/runtime/AgentRuntimeTest.java
git commit -m "feat(runtime): compose rule and llm verifiers"
```

### Task 7: End-to-end verification and doc sync

**Files:**
- Modify: `README.md`
- Modify: `README.zh-CN.md`
- Optional: `docs/plans/2026-03-07-agent-intelligence-stop-loss-ralph-loop-design.md`

**Step 1: Run focused verification suite**
Run: `mvn -Dtest=LlmRequestStructuredOutputTest,StructuredOutputServiceTest,OpenAiCompatibleLlmClientStructuredOutputTest,LlmRuntimeDecisionStageTest,DefaultRuntimeDecisionStageTest,ToolExecutorTest,CompositeRuntimeDecisionStageTest,SystemPromptBuilderTest,ContextBuilderPolicyTest,AgentRuntimeTest test`
Expected: PASS.

**Step 2: Run compile verification**
Run: `mvn -DskipTests compile`
Expected: PASS.

**Step 3: Update docs**
- Add one short README note about structured verifier decisions.
- Update design doc wording if class names changed during implementation.

**Step 4: Commit**
```bash
git add README.md README.zh-CN.md docs/plans/2026-03-07-agent-intelligence-stop-loss-ralph-loop-design.md
git commit -m "docs(runtime): document structured verifier flow"
```
