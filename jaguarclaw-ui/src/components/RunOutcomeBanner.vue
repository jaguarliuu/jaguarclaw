<script setup lang="ts">
import Badge from '@/components/common/Badge.vue'
import { useI18n } from '@/i18n'
import type { RunOutcomePayload } from '@/types'

const props = defineProps<{
  outcome: RunOutcomePayload
}>()

const emit = defineEmits<{
  dismiss: []
}>()

const { t } = useI18n()

function badgeVariant(status?: string): 'default' | 'outline' | 'muted' {
  if (!status) return 'muted'
  if (status === 'BLOCKED_PENDING_USER_DECISION' || status === 'BLOCKED_BY_ENVIRONMENT') {
    return 'outline'
  }
  return 'default'
}
</script>

<template>
  <section class="run-outcome-banner" :class="outcome.planStatus?.toLowerCase() || 'default'">
    <div class="banner-head">
      <div class="banner-title-row">
        <Badge :variant="badgeVariant(outcome.status)">
          {{ outcome.planStatus || outcome.status || t('workspace.runOutcome.title') }}
        </Badge>
        <span class="banner-title">{{ t('workspace.runOutcome.title') }}</span>
      </div>
      <button class="dismiss-btn" type="button" @click="emit('dismiss')">
        {{ t('common.close') }}
      </button>
    </div>

    <p v-if="outcome.message" class="banner-message">{{ outcome.message }}</p>
    <p v-if="outcome.detail && outcome.detail !== outcome.message" class="banner-detail">
      {{ outcome.detail }}
    </p>

    <div class="banner-meta">
      <span v-if="outcome.currentItemId">
        {{ t('workspace.runOutcome.currentItem') }}: <code>{{ outcome.currentItemId }}</code>
      </span>
      <span v-if="outcome.reason">
        {{ t('workspace.runOutcome.reason') }}: <code>{{ outcome.reason }}</code>
      </span>
    </div>

    <div v-if="outcome.pendingQuestion" class="banner-question">
      <div class="question-label">{{ t('workspace.runOutcome.pendingQuestion') }}</div>
      <div class="question-text">{{ outcome.pendingQuestion }}</div>
    </div>
  </section>
</template>

<style scoped>
.run-outcome-banner {
  margin: 0 24px 12px;
  padding: 14px 16px;
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  background: var(--color-gray-50);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.run-outcome-banner.blocked {
  border-color: rgba(var(--color-primary-rgb), 0.28);
  background: rgba(var(--color-primary-rgb), 0.06);
}

.banner-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.banner-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.banner-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-800);
}

.dismiss-btn {
  border: 0;
  background: transparent;
  color: var(--color-gray-600);
  font-size: 12px;
  cursor: pointer;
}

.dismiss-btn:hover {
  color: var(--color-gray-900);
}

.banner-message {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-gray-900);
}

.banner-detail {
  font-size: 13px;
  color: var(--color-gray-700);
  line-height: 1.6;
}

.banner-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--color-gray-600);
}

.banner-meta code {
  font-family: var(--font-mono);
  font-size: 11px;
}

.banner-question {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--color-gray-200);
}

.question-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-gray-500);
  margin-bottom: 4px;
}

.question-text {
  font-size: 14px;
  color: var(--color-gray-900);
  line-height: 1.6;
}
</style>
