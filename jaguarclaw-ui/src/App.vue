<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ConnectionStatus from '@/components/ConnectionStatus.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import { useNotification } from '@/composables/useNotification'

const { state, handleConfirm, handleCancel } = useConfirm()
const { connect, disconnect, state: connectionState } = useWebSocket()
const { setupEventListeners } = useChat()

onMounted(() => {
  connect()
  setupEventListeners()
  useNotification()
})

onUnmounted(() => {
  disconnect()
})
</script>

<template>
  <RouterView />

  <!-- Connection status — fixed top-right -->
  <div class="status-widget">
    <ConnectionStatus :state="connectionState" />
  </div>

  <!-- Global Confirm Dialog -->
  <ConfirmDialog
    :visible="state.visible"
    :title="state.title"
    :message="state.message"
    :confirm-text="state.confirmText"
    :cancel-text="state.cancelText"
    :danger="state.danger"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap');

:root {
  --color-black: #111111;
  --color-white: #ffffff;

  /* ── Accent token (changes per theme) ──────────────────────── */
  --color-primary:       #111111;
  --color-primary-hover: #2a2a2a;
  --color-primary-rgb:   17, 17, 17;

  /* ── Surface tokens (default = Midnight, all white/gray) ────── */
  --sidebar-rail-bg:       #ffffff;
  --sidebar-rail-fg:       var(--color-gray-500);
  --sidebar-rail-hover-bg: var(--color-gray-100);
  --sidebar-rail-logo-fg:  var(--color-black);

  --sidebar-panel-bg:      #ffffff;
  --sidebar-panel-border:  var(--color-gray-200);
  --sidebar-item-hover-bg: var(--color-gray-50);

  --settings-sidebar-bg:   var(--color-gray-50);

  /* Content area background (changes per theme) */
  --content-bg: #ffffff;

  /* Gray scale */
  --color-gray-50:  #f7f7f7;
  --color-gray-100: #f0f0f0;
  --color-gray-200: #e2e2e2;
  --color-gray-300: #c0c0c0;
  --color-gray-400: #999999;
  --color-gray-500: #777777;
  --color-gray-600: #555555;
  --color-gray-700: #444444;
  --color-gray-800: #2a2a2a;
  --color-gray-900: #1a1a1a;

  /* Backward compatibility aliases */
  --color-gray-dark: var(--color-gray-500);
  --color-gray-light: var(--color-gray-200);
  --color-gray-bg: var(--color-gray-50);

  /* Status colors (muted) */
  --color-success: #2a9d5c;
  --color-warning: #c98a0c;
  --color-error: #d44040;
  --color-info: #4a7fd4;

  /* Color scales for badges and status */
  --color-blue-50: #eff6ff;
  --color-blue-600: #2563eb;
  --color-green-50: #f0fdf4;
  --color-green-500: #22c55e;
  --color-green-600: #16a34a;
  --color-purple-50: #faf5ff;
  --color-purple-600: #9333ea;
  --color-red-50: #fef2f2;
  --color-red-200: #fecaca;
  --color-red-500: #ef4444;
  --color-red-600: #dc2626;
  --color-red-700: #b91c1c;

  --font-ui: 'IBM Plex Sans', -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;

  /* Borders */
  --border: 1px solid var(--color-gray-200);
  --border-light: 1px solid var(--color-gray-100);
  --border-strong: 1px solid var(--color-gray-300);

  /* Radius scale */
  --radius-sm: 4px;
  --radius-md: 6px;
  --radius-lg: 8px;
  --radius-xl: 12px;
  --radius-full: 9999px;

  /* Shadow scale */
  --shadow-xs:    0 1px 2px rgba(0,0,0,0.04);
  --shadow-sm:    0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  --shadow-md:    0 4px 12px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.04);
  --shadow-lg:    0 8px 24px rgba(0,0,0,0.08), 0 2px 6px rgba(0,0,0,0.04);
  --shadow-float: 0 12px 32px rgba(0,0,0,0.10), 0 2px 8px rgba(0,0,0,0.04);

  /* Transitions */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
  --duration-fast: 0.15s;
  --duration-normal: 0.2s;

  /* Layout */
  --sidebar-width: 260px;
  --settings-nav-width: 180px;
  --detail-panel-width: 480px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

body {
  font-family: var(--font-ui);
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-black);
  background: var(--color-white);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

::selection {
  background: var(--color-primary);
  color: var(--color-white);
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--color-gray-300);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--color-gray-400);
}

/* ─── Theme definitions ─────────────────────────────────────────
   Each theme overrides BOTH the accent color AND all surface tokens,
   so the entire sidebar (rail + panel) takes on the theme's character.
   ──────────────────────────────────────────────────────────────── */

/* Teal ── C100M5Y5K40 → RGB(0,145,145) = #009191
   Deep teal rail, light teal-tinted session panel                  */
[data-theme="teal"] {
  --color-primary:       #009191;
  --color-primary-hover: #007575;
  --color-primary-rgb:   0, 145, 145;

  --sidebar-rail-bg:       #006b6b;
  --sidebar-rail-fg:       rgba(255, 255, 255, 0.65);
  --sidebar-rail-hover-bg: rgba(255, 255, 255, 0.14);
  --sidebar-rail-logo-fg:  #ffffff;

  --sidebar-panel-bg:      #edf8f8;
  --sidebar-panel-border:  #a8d4d4;
  --sidebar-item-hover-bg: #d8eeee;

  --settings-sidebar-bg:   #edf8f8;
  --content-bg:            #f4fcfc;
}

/* Ocean ── deep navy rail, ice-blue panel */
[data-theme="ocean"] {
  --color-primary:       #1a6cdb;
  --color-primary-hover: #1559b5;
  --color-primary-rgb:   26, 108, 219;

  --sidebar-rail-bg:       #1a3f8a;
  --sidebar-rail-fg:       rgba(255, 255, 255, 0.65);
  --sidebar-rail-hover-bg: rgba(255, 255, 255, 0.14);
  --sidebar-rail-logo-fg:  #ffffff;

  --sidebar-panel-bg:      #eef4ff;
  --sidebar-panel-border:  #a8c4f0;
  --sidebar-item-hover-bg: #d8e8ff;

  --settings-sidebar-bg:   #eef4ff;
  --content-bg:            #f4f8ff;
}

/* Forest ── dark green rail, mint panel */
[data-theme="forest"] {
  --color-primary:       #2d7d46;
  --color-primary-hover: #226037;
  --color-primary-rgb:   45, 125, 70;

  --sidebar-rail-bg:       #1c5230;
  --sidebar-rail-fg:       rgba(255, 255, 255, 0.65);
  --sidebar-rail-hover-bg: rgba(255, 255, 255, 0.14);
  --sidebar-rail-logo-fg:  #ffffff;

  --sidebar-panel-bg:      #edf8f1;
  --sidebar-panel-border:  #a0ccb0;
  --sidebar-item-hover-bg: #d4ecdc;

  --settings-sidebar-bg:   #edf8f1;
  --content-bg:            #f3faf5;
}

/* Grape ── deep violet rail, lavender panel */
[data-theme="grape"] {
  --color-primary:       #6d28d9;
  --color-primary-hover: #5b1fc8;
  --color-primary-rgb:   109, 40, 217;

  --sidebar-rail-bg:       #4a1a96;
  --sidebar-rail-fg:       rgba(255, 255, 255, 0.65);
  --sidebar-rail-hover-bg: rgba(255, 255, 255, 0.14);
  --sidebar-rail-logo-fg:  #ffffff;

  --sidebar-panel-bg:      #f4eeff;
  --sidebar-panel-border:  #c0a0e8;
  --sidebar-item-hover-bg: #e4d0ff;

  --settings-sidebar-bg:   #f4eeff;
  --content-bg:            #f8f4ff;
}

/* Sunset ── dark rust rail, warm ivory panel */
[data-theme="sunset"] {
  --color-primary:       #c2410c;
  --color-primary-hover: #a3350a;
  --color-primary-rgb:   194, 65, 12;

  --sidebar-rail-bg:       #8a2c0a;
  --sidebar-rail-fg:       rgba(255, 255, 255, 0.65);
  --sidebar-rail-hover-bg: rgba(255, 255, 255, 0.14);
  --sidebar-rail-logo-fg:  #ffffff;

  --sidebar-panel-bg:      #fff4ee;
  --sidebar-panel-border:  #e8b898;
  --sidebar-item-hover-bg: #ffe4d0;

  --settings-sidebar-bg:   #fff4ee;
  --content-bg:            #fff9f6;
}

/* ─── Smooth transitions during theme switch ───────────────────── */
html.theme-switching,
html.theme-switching * {
  transition:
    background-color 0.3s ease,
    color 0.3s ease,
    border-color 0.3s ease,
    box-shadow 0.3s ease !important;
}

/* ─── Global .btn-primary follows theme accent ─────────────────── */
.btn-primary {
  background: var(--color-primary) !important;
}
.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-hover) !important;
}

/* ─── Connection status — fixed top-right ───────────────────────── */
.status-widget {
  position: fixed;
  top: 12px;
  right: 16px;
  z-index: 50;
}
</style>
