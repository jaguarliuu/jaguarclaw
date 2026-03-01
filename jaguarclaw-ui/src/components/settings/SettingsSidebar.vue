<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const { currentTheme, themes, setTheme } = useTheme()
const { t, locale, setLocale } = useI18n()

const groups = computed(() => [
  {
    label: t('settings.groups.aiCore'),
    items: [
      { id: 'llm',        label: t('settings.nav.models') },
      { id: 'agents',     label: t('settings.nav.agents') },
      { id: 'tools',      label: t('settings.nav.tools') },
      { id: 'skills',     label: t('settings.nav.skills') },
      { id: 'memory',     label: t('settings.nav.memory') },
      { id: 'mcp',        label: t('settings.nav.mcp') },
      { id: 'soul',       label: t('settings.nav.persona') },
    ]
  },
  {
    label: t('settings.groups.integration'),
    items: [
      { id: 'nodes',       label: t('settings.nav.nodes') },
      { id: 'datasources', label: t('settings.nav.dataSources') },
    ]
  },
  {
    label: t('settings.groups.administration'),
    items: [
      { id: 'system',     label: t('settings.nav.system') },
      { id: 'audit',      label: t('settings.nav.audit') },
      { id: 'tasks',      label: t('settings.nav.schedules') },
      { id: 'heartbeat',  label: t('settings.nav.heartbeat') },
    ]
  }
])

const currentSection = computed(() => {
  return route.params.section as string || 'llm'
})

function navigateTo(sectionId: string) {
  router.push(`/settings/${sectionId}`)
}
</script>

<template>
  <nav class="settings-sidebar">
    <!-- Top: back link + logo -->
    <div class="sidebar-top">
      <button class="back-btn" @click="router.push('/')" :title="t('settings.back')">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M10 3L5 8L10 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>{{ t('settings.back') }}</span>
      </button>
    </div>

    <!-- Grouped nav -->
    <div class="nav-groups">
      <div v-for="group in groups" :key="group.label" class="nav-group">
        <div class="group-label">{{ group.label }}</div>
        <ul class="nav-list">
          <li v-for="item in group.items" :key="item.id">            <button
              class="nav-item"
              :class="{ active: currentSection === item.id }"
              @click="navigateTo(item.id)"
            >
              <!-- Models -->
              <svg v-if="item.id === 'llm'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <rect x="4" y="4" width="8" height="8" rx="1" stroke="currentColor" stroke-width="1.4"/>
                <path d="M6 4V2M10 4V2M6 12v2M10 12v2M4 6H2M4 10H2M12 6h2M12 10h2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                <circle cx="8" cy="8" r="1.5" fill="currentColor"/>
              </svg>
              <!-- Agents -->
              <svg v-else-if="item.id === 'agents'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <circle cx="5.5" cy="6" r="2" stroke="currentColor" stroke-width="1.4"/>
                <circle cx="10.5" cy="6" r="2" stroke="currentColor" stroke-width="1.4"/>
                <path d="M2.5 12.5c.4-1.7 1.8-2.8 3-2.8s2.6 1.1 3 2.8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                <path d="M7.5 12.5c.4-1.7 1.8-2.8 3-2.8s2.6 1.1 3 2.8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- Tools -->
              <svg v-else-if="item.id === 'tools'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <path d="M10 2.5a3 3 0 00-2.12 5.12L3.5 12a1 1 0 001.41 1.41l4.38-4.38A3 3 0 1010 2.5zm0 4.5a1.5 1.5 0 110-3 1.5 1.5 0 010 3z" stroke="currentColor" stroke-width="1.2" fill="none"/>
              </svg>
              <!-- Skills -->
              <svg v-else-if="item.id === 'skills'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <path d="M9 2L4 9h5l-2 5 7-7H9l1-5z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
              </svg>
              <!-- Memory -->
              <svg v-else-if="item.id === 'memory'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <path d="M8 3C5.24 3 3 5.24 3 8v1c0 2.76 2.24 5 5 5s5-2.24 5-5V8c0-2.76-2.24-5-5-5z" stroke="currentColor" stroke-width="1.4"/>
                <path d="M6 8h4M8 6v4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- MCP -->
              <svg v-else-if="item.id === 'mcp'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <path d="M6 2v4M10 2v4M5 6h6v3a3 3 0 01-6 0V6z" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M8 9v5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- Persona -->
              <svg v-else-if="item.id === 'soul'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <circle cx="8" cy="5" r="2.5" stroke="currentColor" stroke-width="1.4"/>
                <path d="M3 14c0-2.76 2.24-5 5-5s5 2.24 5 5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- Nodes -->
              <svg v-else-if="item.id === 'nodes'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <rect x="2" y="3" width="12" height="4" rx="1" stroke="currentColor" stroke-width="1.4"/>
                <rect x="2" y="9" width="12" height="4" rx="1" stroke="currentColor" stroke-width="1.4"/>
                <circle cx="5" cy="5" r="0.75" fill="currentColor"/>
                <circle cx="5" cy="11" r="0.75" fill="currentColor"/>
              </svg>
              <!-- Data Sources -->
              <svg v-else-if="item.id === 'datasources'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <ellipse cx="8" cy="4" rx="5" ry="1.5" stroke="currentColor" stroke-width="1.4"/>
                <path d="M3 4v4c0 .83 2.24 1.5 5 1.5S13 8.83 13 8V4" stroke="currentColor" stroke-width="1.4"/>
                <path d="M3 8v4c0 .83 2.24 1.5 5 1.5S13 12.83 13 12V8" stroke="currentColor" stroke-width="1.4"/>
              </svg>
              <!-- System -->
              <svg v-else-if="item.id === 'system'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.4"/>
                <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M12.6 3.4l-1.4 1.4M4.8 11.2L3.4 12.6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- Audit Log -->
              <svg v-else-if="item.id === 'audit'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <rect x="2" y="2" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.4"/>
                <path d="M5 6h6M5 9h6M5 12h4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
              <!-- Schedules -->
              <svg v-else-if="item.id === 'tasks'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <circle cx="8" cy="8" r="5.5" stroke="currentColor" stroke-width="1.4"/>
                <path d="M8 5v3.5L10.5 10" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <!-- Heartbeat -->
              <svg v-else-if="item.id === 'heartbeat'" width="16" height="16" viewBox="0 0 16 16" fill="none" class="nav-icon">
                <path d="M1 8h3l2-4 2 8 2-4 1 2h4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>

              <span class="nav-label">{{ item.label }}</span>
            </button>
          </li>
        </ul>
      </div>
    </div>

    <!-- Theme + Language picker -->
    <div class="theme-picker">
      <div class="theme-picker-label">{{ t('settings.theme') }}</div>
      <div class="theme-swatches">
        <button
          v-for="theme in themes"
          :key="theme.id"
          class="swatch"
          :class="{ active: currentTheme === theme.id }"
          :style="{ '--swatch-color': theme.color }"
          :title="theme.name"
          @click="setTheme(theme.id)"
        />
      </div>

      <div class="theme-picker-label" style="margin-top: 12px;">{{ t('settings.language') }}</div>
      <div class="lang-switcher">
        <button
          class="lang-btn"
          :class="{ active: locale === 'zh' }"
          @click="setLocale('zh')"
        >中</button>
        <button
          class="lang-btn"
          :class="{ active: locale === 'en' }"
          @click="setLocale('en')"
        >EN</button>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.settings-sidebar {
  width: 220px;
  height: 100%;
  border-right: 1px solid var(--sidebar-panel-border);
  background: var(--settings-sidebar-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* Top area */
.sidebar-top {
  padding: 16px 12px 12px;
  border-bottom: 1px solid var(--sidebar-panel-border);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-500);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  margin-bottom: 12px;
  width: 100%;
  text-align: left;
}

.back-btn:hover {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-black);
}

/* Groups */
.nav-groups {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px 16px;
}

.nav-group {
  margin-bottom: 8px;
}

.group-label {
  padding: 8px 14px 4px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-gray-400);
}

.nav-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 14px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 400;
  text-align: left;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  color: var(--color-gray-600);
}

.nav-item:hover {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-black);
}

.nav-item.active {
  background: var(--color-white);
  color: var(--color-primary);
  font-weight: 500;
  box-shadow: var(--shadow-xs);
}

.nav-icon {
  flex-shrink: 0;
  opacity: 0.7;
}

.nav-item.active .nav-icon {
  opacity: 1;
  color: var(--color-primary);
}

.nav-item.active .nav-icon {
  opacity: 1;
}

.nav-label {
  flex: 1;
}

/* Theme picker */
.theme-picker {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--sidebar-panel-border);
}

.theme-picker-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-gray-400);
  margin-bottom: 10px;
}

.theme-swatches {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.swatch {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: var(--swatch-color);
  cursor: pointer;
  padding: 0;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.swatch:hover {
  transform: scale(1.2);
}

.swatch.active {
  box-shadow: 0 0 0 2px var(--color-white), 0 0 0 4px var(--swatch-color);
}

.lang-switcher {
  display: flex;
  gap: 6px;
}

.lang-btn {
  flex: 1;
  height: 26px;
  border: 1px solid var(--sidebar-panel-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-gray-500);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.lang-btn:hover {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-black);
}

.lang-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}
</style>
