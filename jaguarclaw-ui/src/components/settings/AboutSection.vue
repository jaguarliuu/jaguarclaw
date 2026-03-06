<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from '@/i18n'
import { useSystemInfo } from '@/composables/useSystemInfo'
import { useAboutInfo, type AppPaths } from '@/composables/useAboutInfo'

const { t } = useI18n()
const { systemInfo, environments, refresh: refreshSystem, loading: systemLoading } = useSystemInfo()
const { changelog, appInfo, refresh: refreshAbout, loading: aboutLoading, error, openPath } = useAboutInfo()
const actionError = ref('')
const actionLoading = ref<keyof AppPaths | ''>('')

const isElectron = computed(() => typeof window !== 'undefined' && !!window.electron?.isElectron)
const isLoading = computed(() => systemLoading.value || aboutLoading.value)

const systemRows = computed(() => {
  if (!systemInfo.value) return []
  return [
    { label: t('sections.about.system.os'), value: `${systemInfo.value.os} ${systemInfo.value.osVersion}` },
    { label: t('sections.about.system.architecture'), value: systemInfo.value.architecture },
    { label: t('sections.about.system.java'), value: `${systemInfo.value.javaVersion} · ${systemInfo.value.javaVendor}` },
    { label: t('sections.about.system.cpu'), value: String(systemInfo.value.availableProcessors) },
    { label: t('sections.about.system.user'), value: systemInfo.value.userName },
    { label: t('sections.about.system.home'), value: systemInfo.value.userHome },
  ]
})

const environmentSummary = computed(() => {
  return environments.value.map((env) => ({
    name: env.name,
    value: env.installed ? (env.version || t('sections.about.actions.installed')) : t('sections.about.actions.notInstalled'),
    installed: env.installed,
  }))
})


const expandedReleaseKeys = ref<string[]>([])

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

async function refreshAll() {
  await Promise.all([refreshSystem(), refreshAbout()])
}

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
  refreshAll()
})
</script>

<template>
  <div class="about-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.about') }}</h2>
        <p class="section-subtitle">{{ t('sections.about.subtitle') }}</p>
      </div>
      <div class="header-badge" v-if="appInfo">
        <span class="app-name">{{ appInfo.name }}</span>
        <span class="app-version">v{{ appInfo.version }}</span>
      </div>
    </header>

    <div v-if="isLoading" class="state-card">
      {{ t('sections.about.loading') }}
    </div>

    <div v-else class="about-scroll">
      <div class="hero-card">
        <div>
          <div class="hero-eyebrow">{{ t('sections.about.hero.eyebrow') }}</div>
          <h3 class="hero-title">{{ appInfo?.name || 'JaguarClaw' }}</h3>
          <p class="hero-text">{{ t('sections.about.hero.description') }}</p>
        </div>
        <div class="hero-meta">
          <div class="hero-metric">
            <span class="hero-metric-label">{{ t('sections.about.hero.version') }}</span>
            <strong>{{ appInfo?.version || 'dev' }}</strong>
          </div>
          <div class="hero-metric">
            <span class="hero-metric-label">{{ t('sections.about.hero.environments') }}</span>
            <strong>{{ environmentSummary.length }}</strong>
          </div>
          <div class="hero-metric">
            <span class="hero-metric-label">{{ t('sections.about.hero.releases') }}</span>
            <strong>{{ changelog.length }}</strong>
          </div>
        </div>
      </div>

      <div class="about-grid">
        <section class="card">
          <div class="card-head">
            <h3>{{ t('sections.about.system.title') }}</h3>
            <span class="card-chip">{{ t('sections.about.system.live') }}</span>
          </div>
          <div class="kv-list">
            <div v-for="row in systemRows" :key="row.label" class="kv-item">
              <span class="kv-label">{{ row.label }}</span>
              <span class="kv-value">{{ row.value }}</span>
            </div>
          </div>
          <div class="env-summary">
            <div v-for="env in environmentSummary" :key="env.name" class="env-pill" :class="{ installed: env.installed }">
              <span>{{ env.name }}</span>
              <strong>{{ env.value }}</strong>
            </div>
          </div>
        </section>

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

      <p v-if="error || actionError" class="error-text">{{ error || actionError }}</p>
    </div>
  </div>
</template>

<style scoped>
.about-section {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, rgba(255,255,255,0.6), rgba(255,255,255,0.92));
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid var(--sidebar-panel-border);
  background:
    radial-gradient(circle at top right, rgba(82, 132, 255, 0.15), transparent 28%),
    linear-gradient(180deg, rgba(255,255,255,0.94), rgba(255,255,255,0.78));
}

.section-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-gray-900);
}

.section-subtitle {
  margin: 6px 0 0;
  color: var(--color-gray-600);
  font-size: 13px;
}

.header-badge {
  align-self: flex-start;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255,255,255,0.92);
  border: 1px solid rgba(82, 132, 255, 0.18);
  box-shadow: 0 10px 30px rgba(41, 51, 74, 0.08);
}

.app-name { font-weight: 700; color: var(--color-gray-900); }
.app-version { color: var(--color-gray-600); font-family: var(--font-mono); font-size: 12px; }

.about-scroll {
  flex: 1;
  overflow: auto;
  padding: 24px 28px 40px;
}

.state-card,
.card,
.hero-card {
  border: 1px solid rgba(152, 169, 201, 0.22);
  box-shadow: 0 16px 40px rgba(35, 47, 74, 0.08);
}

.state-card {
  margin: 24px 28px;
  padding: 18px;
  border-radius: 18px;
  background: white;
  color: var(--color-gray-600);
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(24, 42, 80, 0.96), rgba(63, 94, 180, 0.88));
  color: white;
  margin-bottom: 20px;
}

.hero-eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  opacity: 0.72;
}

.hero-title {
  margin: 8px 0 10px;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.hero-text {
  margin: 0;
  max-width: 620px;
  color: rgba(255,255,255,0.78);
  line-height: 1.6;
}

.hero-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, 1fr));
  gap: 12px;
  min-width: 320px;
}

.hero-metric {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(12px);
}

.hero-metric-label {
  display: block;
  margin-bottom: 8px;
  color: rgba(255,255,255,0.7);
  font-size: 12px;
}

.about-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.card {
  padding: 20px;
  border-radius: 22px;
  background: rgba(255,255,255,0.92);
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
}

.card-chip {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #3450a2;
  background: rgba(82, 132, 255, 0.08);
  border: 1px solid rgba(82, 132, 255, 0.12);
  padding: 6px 10px;
  border-radius: 999px;
}

.kv-list { display: grid; gap: 12px; }
.kv-item { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 10px; border-bottom: 1px dashed rgba(152, 169, 201, 0.35); }
.kv-label { color: var(--color-gray-500); font-size: 12px; }
.kv-value { color: var(--color-gray-900); font-weight: 600; text-align: right; }

.env-summary {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.env-pill {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(249, 219, 120, 0.18);
  color: #8b5c00;
}

.env-pill.installed {
  background: rgba(72, 187, 120, 0.14);
  color: #1b6b40;
}

.path-list { display: grid; gap: 12px; }
.path-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.94), rgba(243, 247, 252, 0.86));
  border: 1px solid rgba(152, 169, 201, 0.18);
}
.path-label { font-size: 12px; color: var(--color-gray-500); margin-bottom: 4px; }
.path-value { font-family: var(--font-mono); font-size: 12px; line-height: 1.5; color: var(--color-gray-900); word-break: break-all; }

.action-btn,
.ghost-btn {
  border: none;
  cursor: pointer;
  transition: all 0.18s ease;
}

.action-btn {
  padding: 10px 14px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f5fff, #5c84ff);
  color: white;
  box-shadow: 0 10px 24px rgba(82, 132, 255, 0.24);
}

.ghost-btn {
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(82, 132, 255, 0.08);
  color: #3450a2;
  min-width: 74px;
}

.action-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.release-list { display: grid; gap: 14px; }
.release-item {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 18px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(249, 250, 252, 0.95), rgba(245, 247, 250, 0.9));
  border: 1px solid rgba(152, 169, 201, 0.18);
}
.release-version { font-size: 18px; font-weight: 700; color: var(--color-gray-900); }
.release-date { margin-top: 4px; color: var(--color-gray-500); font-size: 12px; }
.release-toggle { align-self: flex-start; }
.release-body h4 { margin: 0 0 12px; font-size: 15px; color: var(--color-gray-900); }
.release-summary { display: flex; flex-wrap: wrap; gap: 8px; }
.summary-chip { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.summary-chip.added { background: rgba(72, 187, 120, 0.12); color: #1b6b40; }
.summary-chip.changed { background: rgba(82, 132, 255, 0.12); color: #3450a2; }
.summary-chip.fixed { background: rgba(245, 158, 11, 0.16); color: #9a6700; }
.release-sections { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.release-section { border-radius: 14px; padding: 12px 14px; border: 1px solid rgba(152, 169, 201, 0.18); }
.release-section.added { background: rgba(72, 187, 120, 0.08); }
.release-section.changed { background: rgba(82, 132, 255, 0.08); }
.release-section.fixed { background: rgba(245, 158, 11, 0.12); }
.release-section-title { margin-bottom: 8px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-gray-700); font-weight: 700; }
.release-body ul { margin: 0; padding-left: 18px; color: var(--color-gray-700); line-height: 1.65; }
.error-text { margin-top: 16px; color: #b42318; }

@media (max-width: 1080px) {
  .about-grid { grid-template-columns: 1fr; }
  .card-wide { grid-column: auto; }
  .hero-card { flex-direction: column; }
  .hero-meta { grid-template-columns: repeat(3, minmax(0, 1fr)); min-width: 0; }
  .release-item { grid-template-columns: 1fr; }
  .release-sections { grid-template-columns: 1fr; }
}
</style>
