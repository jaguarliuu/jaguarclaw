# About Settings Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove duplicated system content from the Settings About page, align its styling with the app theme system, and display the correct product name for JaguarClaw vs MiniClaw builds.

**Architecture:** Keep `SystemSection` as the single source of truth for runtime/system diagnostics. Reduce `AboutSection` to product identity, local changelog, log entry points, and user data entry points. Replace custom saturated colors with existing theme tokens so the page automatically follows the active color theme. Use Electron-provided app metadata as the primary brand source, with a safe local/dev fallback.

**Tech Stack:** Vue 3, TypeScript, Electron preload/app info bridge, existing settings theme tokens, vue-tsc + Vite build.

---

### Task 1: Remove duplicated system diagnostics from About

**Files:**
- Modify: `jaguarclaw-ui/src/components/settings/AboutSection.vue`
- Verify against: `jaguarclaw-ui/src/components/settings/SystemSection.vue`

**Steps:**
1. Remove system info table and environment summary from `AboutSection`.
2. Keep `SystemSection` as the only detailed system/runtime diagnostics surface.
3. Retain About-only content: product header, changelog, logs, user data paths.
4. Simplify imports/computed state now made unused.

### Task 2: Re-theme About to use system tokens

**Files:**
- Modify: `jaguarclaw-ui/src/components/settings/AboutSection.vue`
- Reference: existing settings token usage in `jaguarclaw-ui/src/components/settings/SettingsSidebar.vue`

**Steps:**
1. Replace highly saturated custom colors with existing CSS variables.
2. Reuse settings card/surface/border/text semantics so theme switching works automatically.
3. Keep hierarchy and readability, but reduce decorative hero treatment.
4. Ensure release chips/sections use subtle token-driven accents instead of hardcoded colors.

### Task 3: Show correct product branding in About

**Files:**
- Modify: `jaguarclaw-ui/src/components/settings/AboutSection.vue`
- Modify if needed: `jaguarclaw-ui/src/composables/useAboutInfo.ts`
- Inspect source: Electron `getAppInfo` payload already used by About page

**Steps:**
1. Use `appInfo.name` as the displayed app name whenever available.
2. Replace hardcoded `JaguarClaw` fallback with a neutral local/dev fallback.
3. Make sure MiniClaw local builds and JaguarClaw packaged builds display the right brand automatically.

### Task 4: Verify the cleanup

**Files:**
- Verify: `jaguarclaw-ui/src/components/settings/AboutSection.vue`

**Steps:**
1. Run frontend type-check/build.
2. Confirm `About` no longer duplicates `System` diagnostics.
3. Confirm theme tokens compile and no hardcoded brand fallback remains.
