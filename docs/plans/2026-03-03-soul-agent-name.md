# Soul Agent Name Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove hardcoded "JaguarClaw" from system prompt; agent name comes from SOUL.md; unnamed agents get a friendly first-conversation prompt to ask the user for a name.

**Architecture:** (1) `SoulConfigService` gains `extractAgentName()` to read the name from SOUL.md; (2) `SystemPromptBuilder.IDENTITY_SECTION` becomes generic (no brand name); (3) when no name is found, a `NAME_REMINDER_INSTRUCTION` is appended to the IDENTITY block so the AI introduces itself warmly and asks for a name; (4) `PromptMode.NONE` resolves the name dynamically.

**Tech Stack:** Java 21, Spring Boot 3, JUnit 5, Mockito, Maven (`./mvnw test`)

---

### Task 1: Add `extractAgentName()` to `SoulConfigService`

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/soul/SoulConfigService.java`
- Test: `src/test/java/com/jaguarliu/ai/soul/AgentScopedSoulConfigServiceTest.java`

**Step 1: Write the failing tests**

Add these two test methods to `AgentScopedSoulConfigServiceTest`:

```java
@Test
@DisplayName("extractAgentName 从 SOUL.md 提取名字")
void extractAgentNameReturnsParsedName(@TempDir Path tempDir) {
    AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
    ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
    SoulConfigService svc = new SoulConfigService(resolver);

    svc.writeSoulMd("agent-x", "# Soul\n\nYour name is Alice.\n\n## Personality\nHelpful.\n");

    assertEquals("Alice", svc.extractAgentName("agent-x"));
}

@Test
@DisplayName("extractAgentName 无名字时返回 null")
void extractAgentNameReturnsNullWhenMissing(@TempDir Path tempDir) {
    AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
    ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
    SoulConfigService svc = new SoulConfigService(resolver);

    svc.writeSoulMd("agent-y", "# Soul\n\n## Personality\nHelpful.\n");

    assertNull(svc.extractAgentName("agent-y"));
}
```

Note: `@TempDir` is already used in the class — declare it as method parameter per test (not class field) because the existing class-level `@TempDir` is already bound.

**Step 2: Run tests to verify they fail**

```bash
./mvnw test -pl . -Dtest=AgentScopedSoulConfigServiceTest -q 2>&1 | tail -20
```

Expected: FAIL — `extractAgentName` method does not exist.

**Step 3: Implement `extractAgentName()` in `SoulConfigService`**

Add this method after `readProfileMd()` in `SoulConfigService.java`:

```java
/**
 * Extracts the agent's name from SOUL.md by looking for a line matching
 * "Your name is <name>". Returns null if not found or SOUL.md is missing.
 */
public String extractAgentName(String agentId) {
    String soul = readSoulMd(agentId);
    if (soul == null || soul.isBlank()) {
        return null;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("Your name is ([^.\\n]+)\\.?")
            .matcher(soul);
    if (m.find()) {
        String name = m.group(1).trim();
        return name.isBlank() ? null : name;
    }
    return null;
}
```

**Step 4: Run tests to verify they pass**

```bash
./mvnw test -pl . -Dtest=AgentScopedSoulConfigServiceTest -q 2>&1 | tail -20
```

Expected: PASS (all 3 tests in the class).

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/soul/SoulConfigService.java \
        src/test/java/com/jaguarliu/ai/soul/AgentScopedSoulConfigServiceTest.java
git commit -m "feat(soul): add extractAgentName() to SoulConfigService"
```

---

### Task 2: Make `IDENTITY_SECTION` generic + add name reminder

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`
- Test: `src/test/java/com/jaguarliu/ai/runtime/SystemPromptBuilderTest.java`

**Step 1: Write the failing tests**

In `SystemPromptBuilderTest.java`, add a new `@Nested` class **before** the existing ones:

```java
@Nested
@DisplayName("Identity and Name Reminder")
class IdentityAndNameReminderTests {

    @Test
    @DisplayName("FULL mode: no name in soul → identity block contains reminder")
    void fullModeNoNameContainsReminder() {
        lenient().when(toolRegistry.listDefinitions()).thenReturn(List.of());
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(skillIndexBuilder.buildIndex("main")).thenReturn("");
        when(soulConfigService.extractAgentName("main")).thenReturn(null);

        String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

        assertTrue(result.contains("First Conversation"), "Should contain name reminder");
        assertTrue(result.contains("don't have a name"), "Should say no name yet");
    }

    @Test
    @DisplayName("FULL mode: name set in soul → no reminder")
    void fullModeNameSetNoReminder() {
        lenient().when(toolRegistry.listDefinitions()).thenReturn(List.of());
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(skillIndexBuilder.buildIndex("main")).thenReturn("");
        when(soulConfigService.extractAgentName("main")).thenReturn("Alice");

        String result = builder.build(SystemPromptBuilder.PromptMode.FULL);

        assertFalse(result.contains("First Conversation"), "Should not contain reminder when name is set");
    }

    @Test
    @DisplayName("NONE mode: name set → includes name in identity")
    void noneModeNameSetIncludesName() {
        when(soulConfigService.extractAgentName("main")).thenReturn("Alice");

        String result = builder.build(SystemPromptBuilder.PromptMode.NONE);

        assertTrue(result.contains("Alice"), "Should include agent name");
        assertTrue(result.contains("AI coding assistant"));
    }

    @Test
    @DisplayName("NONE mode: no name → generic identity")
    void noneModeNoNameGenericIdentity() {
        when(soulConfigService.extractAgentName("main")).thenReturn(null);

        String result = builder.build(SystemPromptBuilder.PromptMode.NONE);

        assertEquals("You are an AI coding assistant.", result);
    }

    @Test
    @DisplayName("IDENTITY section no longer contains JaguarClaw")
    void identityNoLongerHardcodesJaguarClaw() {
        lenient().when(toolRegistry.listDefinitions()).thenReturn(List.of());
        when(toolRegistry.listDefinitions(any(ToolVisibilityResolver.VisibilityRequest.class))).thenReturn(List.of());
        when(skillIndexBuilder.buildIndex("main")).thenReturn("");
        lenient().when(soulConfigService.extractAgentName(any())).thenReturn(null);

        String full = builder.build(SystemPromptBuilder.PromptMode.FULL);
        String minimal = builder.build(SystemPromptBuilder.PromptMode.MINIMAL);

        assertFalse(full.contains("JaguarClaw"), "FULL should not contain hardcoded JaguarClaw");
        assertFalse(minimal.contains("JaguarClaw"), "MINIMAL should not contain hardcoded JaguarClaw");
    }
}
```

**Step 2: Run tests to verify they fail**

```bash
./mvnw test -pl . -Dtest=SystemPromptBuilderTest#IdentityAndNameReminderTests -q 2>&1 | tail -20
```

Expected: FAIL — `extractAgentName` is not called, identity still hardcodes "JaguarClaw".

**Step 3: Implement the changes in `SystemPromptBuilder.java`**

**3a.** Replace the `IDENTITY_SECTION` constant (lines 79–87):

```java
// 身份段落（通用，不含名字 — 名字由 SOUL.md 管理）
private static final String IDENTITY_SECTION = """
    You are an AI coding assistant. You help users with software engineering tasks including:
    - Writing, reviewing, and debugging code
    - Explaining technical concepts
    - File operations and shell commands
    - Creating documents (PPTX, XLSX, etc.)

    Respond concisely and accurately. Use Chinese when the user writes in Chinese.
    """;
```

**3b.** Add `NAME_REMINDER_INSTRUCTION` constant directly after `IDENTITY_SECTION`:

```java
// 无名字时的首次对话提醒
private static final String NAME_REMINDER_INSTRUCTION = """

    **First Conversation — Introduce Yourself**

    You don't have a name yet. At the very start of your FIRST response, \
    warmly introduce yourself to the user. Acknowledge that you're new and \
    unnamed, express that you'd love to have a name, and invite the user to \
    give you one. Keep it light and natural — don't make it feel like a \
    system alert. Adapt the language to the user (Chinese if they write in \
    Chinese). After they provide a name, use the soul tools to save it. \
    Only do this introduction once.
    """;
```

**3c.** Add a new private method `buildIdentitySection(String agentId)` before `buildSkillsSection()`:

```java
/**
 * 构建 Identity 段落，如果 SOUL.md 中没有名字则附加首次对话提醒。
 */
private String buildIdentitySection(String agentId) {
    String base = IDENTITY_SECTION.trim();
    String name = soulConfigService.extractAgentName(agentId);
    if (name == null || name.isBlank()) {
        return base + NAME_REMINDER_INSTRUCTION.stripTrailing();
    }
    return base;
}
```

**3d.** In `build(PromptMode, Set, Set, String, String)`, update the IDENTITY line (line 228):

```java
// Before:
blocks.put("IDENTITY", IDENTITY_SECTION.trim());

// After:
blocks.put("IDENTITY", buildIdentitySection(agentId));
```

**3e.** Fix `PromptMode.NONE` case (lines 219–221):

```java
// Before:
if (mode == PromptMode.NONE) {
    return "You are JaguarClaw, an AI coding assistant.";
}

// After:
if (mode == PromptMode.NONE) {
    String name = soulConfigService.extractAgentName(agentId);
    if (name != null && !name.isBlank()) {
        return "You are " + name + ", an AI coding assistant.";
    }
    return "You are an AI coding assistant.";
}
```

**Step 4: Run the new tests to verify they pass**

```bash
./mvnw test -pl . -Dtest=SystemPromptBuilderTest#IdentityAndNameReminderTests -q 2>&1 | tail -20
```

Expected: PASS.

**Step 5: Fix existing tests that assert "JaguarClaw"**

Now update the existing assertions that assumed "JaguarClaw". Search the test file for all occurrences:

- `NoneModeTests.returnsMinimalIdentity` (line 84): change assertion to `assertEquals("You are an AI coding assistant.", result)` — but you also need to add `when(soulConfigService.extractAgentName("main")).thenReturn(null)` in setUp or in the test.

- `MinimalModeTests.containsIdentity` (line 102): change `assertTrue(result.contains("You are JaguarClaw"))` → `assertTrue(result.contains("You are an AI coding assistant"))`; also add `lenient().when(soulConfigService.extractAgentName(any())).thenReturn(null)` to `setUp()`.

- `FullModeTests.containsIdentity` (line 177): change `assertTrue(result.contains("You are JaguarClaw"))` → `assertTrue(result.contains("You are an AI coding assistant"))`.

- `SectionOrderTests.fullModeCorrectOrder` (line 576): change `result.indexOf("You are JaguarClaw")` → `result.indexOf("You are an AI coding assistant")`.

Also add to `setUp()`:
```java
lenient().when(soulConfigService.extractAgentName(any())).thenReturn(null);
```

**Step 6: Run the full test class to verify everything passes**

```bash
./mvnw test -pl . -Dtest=SystemPromptBuilderTest -q 2>&1 | tail -30
```

Expected: all tests PASS.

**Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java \
        src/test/java/com/jaguarliu/ai/runtime/SystemPromptBuilderTest.java
git commit -m "feat(prompt): remove hardcoded JaguarClaw; agent name from soul with first-conversation reminder"
```

---

### Task 3: Fix `defaultSoulMd()` for nameless agents

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/soul/SoulConfigService.java`
- Test: `src/test/java/com/jaguarliu/ai/soul/AgentScopedSoulConfigServiceTest.java`

**Step 1: Write the failing test**

Add to `AgentScopedSoulConfigServiceTest`:

```java
@Test
@DisplayName("ensureAgentDefaults with null displayName creates SOUL.md without name line")
void ensureAgentDefaultsNullNameCreatesNoNameLine(@TempDir Path tempDir) {
    AgentWorkspaceResolver resolver = new AgentWorkspaceResolver(Optional.empty());
    ReflectionTestUtils.setField(resolver, "workspaceRoot", tempDir.toString());
    SoulConfigService svc = new SoulConfigService(resolver);

    svc.ensureAgentDefaults("nameless-agent", null);

    String soul = svc.readSoulMd("nameless-agent");
    assertFalse(soul.contains("Your name is"), "SOUL.md should not contain a name when none given");
    assertNull(svc.extractAgentName("nameless-agent"), "extractAgentName should return null");
}
```

**Step 2: Run test to verify it fails**

```bash
./mvnw test -pl . -Dtest=AgentScopedSoulConfigServiceTest -q 2>&1 | tail -20
```

Expected: FAIL — current `defaultSoulMd()` writes `"Your name is main"` (falls back to agentId).

**Step 3: Fix `defaultSoulMd()` in `SoulConfigService`**

Change the method (line 139–147) to omit the name line when agentName is null/blank:

```java
private static String defaultSoulMd(String agentName) {
    StringBuilder sb = new StringBuilder("# Soul\n\n");
    if (agentName != null && !agentName.isBlank()) {
        sb.append("Your name is ").append(agentName).append(".\n\n");
    }
    sb.append("## Personality\n");
    sb.append("A helpful and professional AI assistant.\n\n");
    sb.append("## Response Style\n");
    sb.append("- Tone: balanced\n");
    sb.append("- Detail level: balanced\n");
    return sb.toString();
}
```

Also update `ensureAgentDefaults(String agentId, String displayName)` (line 67–69) — change the `agentName` fallback to null instead of `agentId`:

```java
// Before:
String agentName = (displayName != null && !displayName.isBlank()) ? displayName : resolvedAgentId;

// After:
String agentName = (displayName != null && !displayName.isBlank()) ? displayName : null;
```

**Step 4: Run all soul tests to verify they pass**

```bash
./mvnw test -pl . -Dtest=AgentScopedSoulConfigServiceTest -q 2>&1 | tail -20
```

Expected: PASS.

**Step 5: Run all tests to verify nothing is broken**

```bash
./mvnw test -q 2>&1 | tail -30
```

Expected: BUILD SUCCESS.

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/soul/SoulConfigService.java \
        src/test/java/com/jaguarliu/ai/soul/AgentScopedSoulConfigServiceTest.java
git commit -m "fix(soul): defaultSoulMd omits name line when no displayName provided"
```
