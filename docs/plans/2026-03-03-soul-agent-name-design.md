# Design: Agent Name from Soul

**Date**: 2026-03-03
**Status**: Approved

## Problem

`SystemPromptBuilder.IDENTITY_SECTION` hardcodes `"You are JaguarClaw, an AI coding assistant."`,
which overrides any name defined in an agent's `SOUL.md`. In a multi-agent system where each
agent can have its own soul, this causes identity confusion — the agent's self-description
contradicts what its soul says.

## Solution: Approach A — Generic IDENTITY, name owned by SOUL

Remove the hardcoded name from `IDENTITY_SECTION`. The `SOUL` block (rendered from `SOUL.md`
and injected immediately after `IDENTITY`) already contains `Your name is X`, so the model
receives a single, consistent identity. No parsing of the soul file is required in the identity
block itself.

For the first-conversation experience, detect whether a name has been set and inject a
friendly self-introduction instruction so the AI proactively asks the user for a name.

## Changes

### 1. `SoulConfigService.java`

- **`defaultSoulMd(String agentName)`**: when `agentName` is null/blank, generate SOUL.md
  *without* a `Your name is` line (just personality/style scaffold).
- **New `extractAgentName(String agentId)`**: reads SOUL.md and extracts the name from the
  first line matching `Your name is (.+)`. Returns `null` if not found.

### 2. `SystemPromptBuilder.java`

- **`IDENTITY_SECTION`** (static constant): remove "JaguarClaw", make generic:
  ```
  You are an AI coding assistant. You help users with software engineering tasks...
  ```

- **`buildIdentitySection(String agentId)`** (new private method): returns `IDENTITY_SECTION`,
  plus appends `NAME_REMINDER_INSTRUCTION` when `extractAgentName(agentId)` returns empty.

- **`NAME_REMINDER_INSTRUCTION`** (static constant):
  ```
  **First Conversation — Introduce Yourself**

  You don't have a name yet. At the very start of your FIRST response,
  warmly introduce yourself to the user. Acknowledge that you're new and
  unnamed, express that you'd love to have a name, and invite the user to
  give you one. Keep it light and natural — don't make it feel like a
  system alert. Adapt the language to the user (Chinese if they write in
  Chinese). After they provide a name, use the soul tools to save it.
  Only do this introduction once.
  ```

- **`build()` FULL mode**: `blocks.put("IDENTITY", buildIdentitySection(agentId))` instead of
  `IDENTITY_SECTION.trim()`.

- **`build()` NONE mode**: dynamically compose identity string using `extractAgentName()`:
  - Name found → `"You are {name}, an AI coding assistant."`
  - Name missing → `"You are an AI coding assistant."`

### 3. `AgentProfileService.java` / `AgentProfileBootstrapInitializer.java`

- When creating a new agent without a display name, call
  `soulConfigService.ensureAgentDefaults(agentId, null)` (already passes null → no-name path).
- No change needed here if `SoulConfigService.defaultSoulMd()` correctly handles null.

## Data Flow

```
Agent created (no display name)
  └─ SOUL.md initialized without "Your name is" line

build(FULL, ..., agentId)
  └─ extractAgentName(agentId) → null
  └─ buildIdentitySection() → IDENTITY + NAME_REMINDER_INSTRUCTION
  └─ AI first response: warmly introduces itself, asks user for a name

User provides name → AI calls update_soul
  └─ SOUL.md updated: "Your name is Alice"

Next build(...)
  └─ extractAgentName(agentId) → "Alice"
  └─ buildIdentitySection() → IDENTITY only (no reminder)
  └─ SOUL block: "Your name is Alice" — consistent identity ✓
```

## Out of Scope

- `PromptMode.MINIMAL` (subagent mode): no SOUL block, no name reminder — subagents inherit
  context from their parent task and don't need a personal greeting.
- UI-layer changes: no frontend modifications needed.
