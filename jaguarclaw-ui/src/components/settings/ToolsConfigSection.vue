<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useToolConfig } from '@/composables/useToolConfig'
import { useI18n } from '@/i18n'
import ConfigCard from '@/components/common/ConfigCard.vue'
import TrustedDomainsModal from './modals/TrustedDomainsModal.vue'
import SearchProvidersModal from './modals/SearchProvidersModal.vue'
import CommandSafetyModal from './modals/CommandSafetyModal.vue'
import DeliveryConfigModal from './modals/DeliveryConfigModal.vue'

const { config, loading, error, getConfig, saveConfig } = useToolConfig()
const { t } = useI18n()

// Modal state
const activeModal = ref<'domains' | 'providers' | 'safety' | 'delivery' | null>(null)

// Save state
const saving = ref(false)
const saveError = ref<string | null>(null)
const saveSuccess = ref(false)

// Computed summaries for cards
const domainsSummary = computed(() => {
  if (!config.value) return ''
  const total = config.value.trustedDomains.defaults.length + config.value.trustedDomains.user.length
  return `${total} domains allowed (${config.value.trustedDomains.user.length} custom)`
})

const providersSummary = computed(() => {
  if (!config.value) return ''
  const enabled = config.value.searchProviders.filter(p => p.enabled).length + 1 // +1 for DuckDuckGo
  return `${enabled} providers enabled`
})

const safetySummary = computed(() => {
  if (!config.value) return ''
  const toolsCount = config.value.hitl?.alwaysConfirmTools?.length || 0
  const keywordsCount = config.value.hitl?.dangerousKeywords?.length || 0
  return `${toolsCount} tools, ${keywordsCount} keywords configured`
})

const deliverySummary = computed(() => {
  if (!config.value) return ''
  const { email, webhook } = currentDelivery()
  const emailStatus = email?.enabled ? (email?.configured ? 'enabled' : 'enabled/not-configured') : 'disabled'
  const webhookCount = webhook?.endpoints?.length || 0
  const webhookStatus = webhook?.enabled ? `enabled (${webhookCount} endpoints)` : 'disabled'
  return `Email: ${emailStatus}, Webhook: ${webhookStatus}`
})

function currentDelivery() {
  return {
    email: config.value?.delivery?.email || {
      enabled: false,
      host: '',
      port: 587,
      username: '',
      from: '',
      tls: true,
      password: '',
      configured: false
    },
    webhook: config.value?.delivery?.webhook || {
      enabled: false,
      configured: false,
      endpoints: []
    }
  }
}

function currentDeliveryForSave() {
  const { email, webhook } = currentDelivery()
  return {
    email: {
      enabled: !!email.enabled,
      host: email.host || '',
      port: Number(email.port) || 587,
      username: email.username || '',
      from: email.from || '',
      tls: email.tls ?? true,
      password: email.password || ''
    },
    webhook: {
      enabled: !!webhook.enabled,
      endpoints: (webhook.endpoints || []).map(ep => ({
        alias: ep.alias || '',
        url: ep.url || '',
        method: ep.method || 'POST',
        trigger: ep.trigger,
        enabled: ep.enabled ?? true,
        headers: { ...(ep.headers || {}) }
      }))
    }
  }
}

function currentDeliveryForModal() {
  const { email, webhook } = currentDelivery()
  return {
    email: { ...email },
    webhook: {
      ...webhook,
      endpoints: (webhook.endpoints || []).map(ep => ({
        ...ep,
        headers: { ...(ep.headers || {}) }
      }))
    }
  }
}

async function handleSaveDomains(domains: string[]) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: domains,
      searchProviders: config.value?.searchProviders.map(p => ({
        type: p.type,
        apiKey: '',
        enabled: p.enabled
      })) || [],
      hitl: {
        alwaysConfirmTools: Array.from(config.value?.hitl?.alwaysConfirmTools || []),
        dangerousKeywords: Array.from(config.value?.hitl?.dangerousKeywords || [])
      },
      delivery: currentDeliveryForSave()
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : t('sections.tools.errors.failedToSave')
  } finally {
    saving.value = false
  }
}

async function handleSaveProviders(providers: { type: string; apiKey: string; enabled: boolean }[]) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: Array.from(config.value?.trustedDomains.user || []),
      searchProviders: providers,
      hitl: {
        alwaysConfirmTools: Array.from(config.value?.hitl?.alwaysConfirmTools || []),
        dangerousKeywords: Array.from(config.value?.hitl?.dangerousKeywords || [])
      },
      delivery: currentDeliveryForSave()
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : t('sections.tools.errors.failedToSave')
  } finally {
    saving.value = false
  }
}

async function handleSaveSafety(data: { alwaysConfirmTools: string[]; dangerousKeywords: string[] }) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: Array.from(config.value?.trustedDomains.user || []),
      searchProviders: config.value?.searchProviders.map(p => ({
        type: p.type,
        apiKey: '',
        enabled: p.enabled
      })) || [],
      hitl: data,
      delivery: currentDeliveryForSave()
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : t('sections.tools.errors.failedToSave')
  } finally {
    saving.value = false
  }
}

async function handleSaveDelivery(data: {
  email: {
    enabled: boolean
    host: string
    port: number
    username: string
    from: string
    tls: boolean
    password: string
  }
  webhook: {
    enabled: boolean
    endpoints: Array<{
      alias: string
      url: string
      method: string
      trigger?: string
      enabled: boolean
      headers: Record<string, string>
    }>
  }
}) {
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await saveConfig({
      userDomains: Array.from(config.value?.trustedDomains.user || []),
      searchProviders: config.value?.searchProviders.map(p => ({
        type: p.type,
        apiKey: '',
        enabled: p.enabled
      })) || [],
      hitl: {
        alwaysConfirmTools: Array.from(config.value?.hitl?.alwaysConfirmTools || []),
        dangerousKeywords: Array.from(config.value?.hitl?.dangerousKeywords || [])
      },
      delivery: data
    })
    await getConfig()
    saveSuccess.value = true
    activeModal.value = null
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : t('sections.tools.errors.failedToSave')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await getConfig()
})
</script>

<template>
  <div class="tools-section">
    <header class="section-header">
      <div>
        <h2 class="section-title">{{ t('settings.nav.tools') }}</h2>
        <p class="section-subtitle">{{ t('sections.tools.subtitle') }}</p>
      </div>
    </header>

    <!-- Loading -->
    <div v-if="loading && !config" class="loading-state">{{ t('sections.tools.loading') }}</div>

    <!-- Error -->
    <div v-if="error && !config" class="error-state">
      <p>{{ error }}</p>
      <button class="retry-btn" @click="getConfig">{{ t('common.retry') }}</button>
    </div>

    <!-- Save Success -->
    <div v-if="saveSuccess" class="save-success">{{ t('sections.tools.savedSuccess') }}</div>

    <!-- Save Error -->
    <div v-if="saveError" class="save-error">{{ saveError }}</div>

    <!-- Config Cards -->
    <template v-if="config">
      <div class="cards-grid">
        <ConfigCard
          :title="t('sections.tools.cards.trustedDomains')"
          :description="t('sections.tools.cards.trustedDomainsDesc')"
          :summary="domainsSummary"
          @click="activeModal = 'domains'"
        />

        <ConfigCard
          :title="t('sections.tools.cards.searchProviders')"
          :description="t('sections.tools.cards.searchProvidersDesc')"
          :summary="providersSummary"
          @click="activeModal = 'providers'"
        />

        <ConfigCard
          :title="t('sections.tools.cards.commandSafety')"
          :description="t('sections.tools.cards.commandSafetyDesc')"
          :summary="safetySummary"
          :badge="{ text: t('sections.tools.cards.securityBadge'), variant: 'warning' }"
          @click="activeModal = 'safety'"
        />

        <ConfigCard
          title="Delivery Tools"
          description="Configure send_email and send_webhook tool visibility and targets."
          :summary="deliverySummary"
          @click="activeModal = 'delivery'"
        />
      </div>
    </template>

    <!-- Modals -->
    <TrustedDomainsModal
      v-if="activeModal === 'domains' && config"
      :default-domains="Array.from(config.trustedDomains.defaults)"
      :user-domains="Array.from(config.trustedDomains.user)"
      @close="activeModal = null"
      @save="handleSaveDomains"
    />

    <SearchProvidersModal
      v-if="activeModal === 'providers' && config"
      :providers="config.searchProviders.map(p => ({
        type: p.type,
        displayName: p.displayName,
        keyRequired: p.keyRequired,
        apiKey: p.apiKey,
        enabled: p.enabled,
        apiKeyUrl: p.apiKeyUrl || ''
      }))"
      @close="activeModal = null"
      @save="handleSaveProviders"
    />

    <CommandSafetyModal
      v-if="activeModal === 'safety' && config"
      :always-confirm-tools="Array.from(config.hitl?.alwaysConfirmTools || [])"
      :dangerous-keywords="Array.from(config.hitl?.dangerousKeywords || [])"
      @close="activeModal = null"
      @save="handleSaveSafety"
    />

    <DeliveryConfigModal
      v-if="activeModal === 'delivery' && config"
      :email="currentDeliveryForModal().email"
      :webhook="currentDeliveryForModal().webhook"
      @close="activeModal = null"
      @save="handleSaveDelivery"
    />
  </div>
</template>

<style scoped>
.tools-section {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}

.section-subtitle {
  font-size: 14px;
  color: var(--color-gray-dark);
}

.loading-state,
.error-state {
  padding: 40px;
  text-align: center;
  color: var(--color-gray-dark);
}

.retry-btn {
  margin-top: 12px;
  padding: 8px 16px;
  border: var(--border);
  border-radius: var(--radius-md);
  background: var(--color-white);
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
}

.retry-btn:hover {
  background: var(--color-gray-bg);
}

.save-success {
  padding: 10px 14px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: var(--radius-md);
  color: #166534;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.save-error {
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--radius-md);
  color: #dc2626;
  font-size: 13px;
  margin-bottom: 16px;
  max-width: 800px;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  max-width: 1200px;
}

@media (max-width: 768px) {
  .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
