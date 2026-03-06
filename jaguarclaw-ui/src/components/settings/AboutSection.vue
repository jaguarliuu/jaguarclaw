<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from '@/i18n'
import { useAboutInfo, type AppPaths } from '@/composables/useAboutInfo'

const { t } = useI18n()
const { changelog, appInfo, refresh, loading, error, openPath } = useAboutInfo()
const actionError = ref('')
const actionLoading = ref<keyof AppPaths | ''>('')
const expandedReleaseKeys = ref<string[]>([])

const isElectron = computed(() => typeof window !== 'undefined' && !!window.electron?.isElectron)
const displayAppName = computed(() => appInfo.value?.name?.trim() || t('sections.about.hero.localBuild'))
const displayVersion = computed(() => appInfo.value?.version || 'dev')

function releaseKey(version: string, date: string) {
  return `${version}-${date}`
}

function isExpanded(version: string, date: string) {
  return expandedReleaseKeys.value.includes(releaseKey(version, date))
}

function toggleRelease(version: string, date: string) {
  const key = releaseKey(version, date)
  if (expandedReleaseKeys.value.includes(key)) {
    expandedReleaseKeys.value = expandedReleaseKeys.value.filter((item) => item !== key)
    return
  }
  expandedReleaseKeys.value = [...expandedReleaseKeys.value, key]
}

const releaseSummary = (entry: { sections: { added: string[]; changed: string[]; fixed: string[] } }) => [
  { label: t('sections.about.changelog.added'), count: entry.sections.added.length, variant: 'added' },
  { label: t('sections.about.changelog.changed'), count: entry.sections.changed.length, variant: 'changed' },
  { label: t('sections.about.changelog.fixed'), count: entry.sections.fixed.length, variant: 'fixed' },
].filter((item) => item.count > 0)

watch(changelog, (entries) => {
  if (!entries.length || expandedReleaseKeys.value.length > 0) {
    return
  }
  const firstEntry = entries[0]
  if (!firstEntry) {
    return
  }
  expandedReleaseKeys.value = [releaseKey(firstEntry.version, firstEntry.date)]
})

const logTargets = computed(() => {
  if (!appInfo.value) return []
  return [
    { key: 'logs', label: t('sections.about.logs.directory'), path: appInfo.value.paths.logs },
    { key: 'startupLog', label: t('sections.about.logs.startup'), path: appInfo.value.paths.startupLog },
    { key: 'desktopLog', label: t('sections.about.logs.desktop'), path: appInfo.value.paths.desktopLog },
    { key: 'backendBridgeLog', label: t('sections.about.logs.backend'), path: appInfo.value.paths.backendBridgeLog },
  ] as Array<{ key: keyof AppPaths; label: string; path: string }>
})

const dataTargets = computed(() => {
  if (!appInfo.value) return []
  return [
    { key: 'appData', label: t('sections.about.paths.appData'), path: appInfo.value.paths.appData },
    { key: 'data', label: t('sections.about.paths.data'), path: appInfo.value.paths.data },
    { key: 'workspace', label: t('sections.about.paths.workspace'), path: appInfo.value.paths.workspace },
    { key: 'skills', label: t('sections.about.paths.skills'), path: appInfo.value.paths.skills },
  ] as Array<{ key: keyof AppPaths; label: string; path: string }>
})

const infoMetrics = computed(() => [
  { label: t('sections.about.hero.version'), value: displayVersion.value },
  { label: t('sections.about.hero.releases'), value: String(changelog.value.length) },
  { label: t('sections.about.logs.title'), value: String(logTargets.value.length) },
  { label: t('sections.about.paths.title'), value: String(dataTargets.value.length) },
])

async function handleOpen(target: keyof AppPaths) {
  actionError.value = ''
  actionLoading.value = target
  try {
    const result = await openPath(target)
    if (!result.success) {
      actionError.value = result.error || t('sections.about.actions.openFailed')
    }
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : t('sections.about.actions.openFailed')
  } finally {
    actionLoading.value = ''
  }
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div class="about-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.about') }}</h2>
        <p class="section-subtitle">{{ t('sections.about.subtitle') }}</p>
      </div>
      <div v-if="appInfo" class="app-badge">
        <span class="app-name">{{ displayAppName }}</span>
        <span class="app-version">v{{ displayVersion }}</span>
      </div>
    </header>

    <div v-if="loading" class="state-card">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="state-card error-state">{{ error }}</div>
    <div v-else class="about-scroll">
      <section class="hero-card">
        <div class="hero-copy">
          <div class="hero-eyebrow">{{ t('settings.nav.about') }}</div>
          <h3 class="hero-title">{{ displayAppName }}</h3>
          <p class="hero-text">{{ t('sections.about.hero.description') }}</p>
        </div>
        <div class="hero-meta">
          <div v-for="metric in infoMetrics" :key="metric.label" class="hero-metric">
            <span class="hero-metric-label">{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </div>
        </div>
      </section>

      <div class="about-grid">
        <section class="card">
          <div class="card-head">
            <h3>{{ t('sections.about.logs.title') }}</h3>
            <button v-if="isElectron" class="action-btn" @click="handleOpen('logs')" :disabled="actionLoading === 'logs'">
              {{ actionLoading === 'logs' ? t('sections.about.actions.opening') : t('sections.about.actions.openDirectory') }}
            </button>
          </div>
          <div class="path-list">
            <div v-for="item in logTargets" :key="item.key" class="path-card">
              <div>
                <div class="path-label">{{ item.label }}</div>
                <div class="path-value">{{ item.path }}</div>
              </div>
              <button v-if="isElectron" class="ghost-btn" @click="handleOpen(item.key)" :disabled="actionLoading === item.key">
                {{ t('sections.about.actions.open') }}
              </button>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3>{{ t('sections.about.paths.title') }}</h3>
            <button v-if="isElectron" class="action-btn" @click="handleOpen('appData')" :disabled="actionLoading === 'appData'">
              {{ actionLoading === 'appData' ? t('sections.about.actions.opening') : t('sections.about.actions.openUserData') }}
            </button>
          </div>
          <div class="path-list">
            <div v-for="item in dataTargets" :key="item.key" class="path-card">
              <div>
                <div class="path-label">{{ item.label }}</div>
                <div class="path-value">{{ item.path }}</div>
              </div>
              <button v-if="isElectron" class="ghost-btn" @click="handleOpen(item.key)" :disabled="actionLoading === item.key">
                {{ t('sections.about.actions.open') }}
              </button>
            </div>
          </div>
        </section>

        <section class="card card-wide">
          <div class="card-head">
            <h3>{{ t('sections.about.changelog.title') }}</h3>
            <span class="card-chip">{{ t('sections.about.changelog.local') }}</span>
          </div>
          <div class="release-list">
            <article v-for="entry in changelog" :key="`${entry.version}-${entry.date}`" class="release-item">
              <div class="release-meta">
                <div>
                  <div class="release-version">v{{ entry.version }}</div>
                  <div class="release-date">{{ entry.date }}</div>
                </div>
                <button class="ghost-btn release-toggle" @click="toggleRelease(entry.version, entry.date)">
                  {{ isExpanded(entry.version, entry.date) ? t('sections.about.changelog.collapse') : t('sections.about.changelog.expand') }}
                </button>
              </div>
              <div class="release-body">
                <h4>{{ entry.title }}</h4>
                <div v-if="!isExpanded(entry.version, entry.date)" class="release-summary">
                  <span v-for="item in releaseSummary(entry)" :key="`${entry.version}-${item.label}`" class="summary-chip" :class="item.variant">
                    {{ item.label }} · {{ item.count }}
                  </span>
                </div>
                <div v-else class="release-sections">
                  <section v-if="entry.sections.added.length" class="release-section added">
                    <div class="release-section-title">{{ t('sections.about.changelog.added') }}</div>
                    <ul>
                      <li v-for="item in entry.sections.added" :key="`added-${item}`">{{ item }}</li>
                    </ul>
                  </section>
                  <section v-if="entry.sections.changed.length" class="release-section changed">
                    <div class="release-section-title">{{ t('sections.about.changelog.changed') }}</div>
                    <ul>
                      <li v-for="item in entry.sections.changed" :key="`changed-${item}`">{{ item }}</li>
                    </ul>
                  </section>
                  <section v-if="entry.sections.fixed.length" class="release-section fixed">
                    <div class="release-section-title">{{ t('sections.about.changelog.fixed') }}</div>
                    <ul>
                      <li v-for="item in entry.sections.fixed" :key="`fixed-${item}`">{{ item }}</li>
                    </ul>
                  </section>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>

      <p v-if="actionError" class="error-text">{{ actionError }}</p>
    </div>
  </div>
</template>

<style scoped>
.about-section {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--settings-sidebar-bg);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 20px;
  border-bottom: 1px solid var(--sidebar-panel-border);
  background: var(--sidebar-panel-bg);
}

.section-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-gray-900);
}

.section-subtitle {
  margin: 6px 0 0;
  color: var(--color-gray-600);
  font-size: 13px;
}

.app-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-full);
  border: 1px solid var(--sidebar-panel-border);
  background: var(--sidebar-panel-bg);
}

.app-name {
  font-weight: 700;
  color: var(--color-gray-900);
}

.app-version {
  color: var(--color-gray-600);
  font-family: var(--font-mono);
  font-size: 12px;
}

.about-scroll {
  flex: 1;
  overflow: auto;
  padding: 24px 28px 40px;
}

.state-card,
.card,
.hero-card {
  border: 1px solid var(--sidebar-panel-border);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.state-card {
  margin: 24px 28px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--sidebar-panel-bg);
  color: var(--color-gray-600);
}

.error-state,
.error-text {
  color: #b42318;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  border-radius: 20px;
  background: var(--sidebar-panel-bg);
  margin-bottom: 20px;
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-gray-500);
}

.hero-title {
  margin: 8px 0 10px;
  font-size: 28px;
  letter-spacing: -0.03em;
  color: var(--color-gray-900);
}

.hero-text {
  margin: 0;
  max-width: 620px;
  color: var(--color-gray-600);
  line-height: 1.6;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(110px, 1fr));
  gap: 12px;
  min-width: 280px;
}

.hero-metric {
  padding: 14px;
  border-radius: var(--radius-xl);
  background: rgba(var(--color-primary-rgb), 0.06);
  border: 1px solid rgba(var(--color-primary-rgb), 0.14);
}

.hero-metric-label {
  display: block;
  margin-bottom: 8px;
  color: var(--color-gray-500);
  font-size: 12px;
}

.hero-metric strong {
  color: var(--color-gray-900);
}

.about-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.card {
  padding: 20px;
  border-radius: 20px;
  background: var(--sidebar-panel-bg);
}

.card-wide {
  grid-column: 1 / -1;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}

.card-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--color-gray-900);
}

.card-chip {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.08);
  border: 1px solid rgba(var(--color-primary-rgb), 0.14);
  padding: 6px 10px;
  border-radius: var(--radius-full);
}

.path-list {
  display: grid;
  gap: 12px;
}

.path-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 16px;
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
}

.path-label {
  font-size: 12px;
  color: var(--color-gray-500);
  margin-bottom: 4px;
}

.path-value {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-gray-900);
  word-break: break-all;
}

.action-btn,
.ghost-btn {
  border: none;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
  font-family: var(--font-ui);
}

.action-btn {
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: #fff;
}

.action-btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
}

.ghost-btn {
  padding: 8px 12px;
  border-radius: var(--radius-lg);
  background: rgba(var(--color-primary-rgb), 0.08);
  color: var(--color-primary);
  min-width: 74px;
}

.ghost-btn:hover:not(:disabled) {
  background: rgba(var(--color-primary-rgb), 0.14);
}

.action-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.release-list {
  display: grid;
  gap: 14px;
}

.release-item {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 18px;
  padding: 16px;
  border-radius: 18px;
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
}

.release-version {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-gray-900);
}

.release-date {
  margin-top: 4px;
  color: var(--color-gray-500);
  font-size: 12px;
}

.release-toggle {
  align-self: flex-start;
}

.release-body h4 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--color-gray-900);
}

.release-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.summary-chip {
  padding: 7px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  border: 1px solid rgba(var(--color-primary-rgb), 0.12);
}

.summary-chip.added,
.summary-chip.changed,
.summary-chip.fixed {
  background: rgba(var(--color-primary-rgb), 0.08);
  color: var(--color-primary);
}

.release-sections {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.release-section {
  border-radius: 14px;
  padding: 12px 14px;
  border: 1px solid rgba(var(--color-primary-rgb), 0.12);
  background: rgba(var(--color-primary-rgb), 0.05);
}

.release-section-title {
  margin-bottom: 8px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-gray-700);
  font-weight: 700;
}

.release-body ul {
  margin: 0;
  padding-left: 18px;
  color: var(--color-gray-700);
  line-height: 1.65;
}

.error-text {
  margin-top: 16px;
}

@media (max-width: 1080px) {
  .about-grid {
    grid-template-columns: 1fr;
  }

  .card-wide {
    grid-column: auto;
  }

  .hero-card {
    flex-direction: column;
  }

  .hero-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    min-width: 0;
  }

  .release-item,
  .release-sections {
    grid-template-columns: 1fr;
  }
}
</style>
