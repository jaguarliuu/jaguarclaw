<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useIm } from '@/composables/useIm'

const { settings, loadSettings, saveSettings } = useIm()

const displayName = ref('')
const redisUrl    = ref('')
const redisPassword = ref('')
const saving = ref(false)
const saved  = ref(false)

onMounted(async () => {
  await loadSettings()
  displayName.value = settings.value?.displayName ?? ''
  redisUrl.value    = settings.value?.redisUrl ?? ''
})

async function save() {
  saving.value = true
  saved.value  = false
  try {
    await saveSettings({ displayName: displayName.value, redisUrl: redisUrl.value, redisPassword: redisPassword.value })
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } finally { saving.value = false }
}
</script>

<template>
  <div class="im-config">
    <h2>IM Settings</h2>
    <p class="node-id" v-if="settings">Node ID: <code>{{ settings.nodeId }}</code></p>

    <div class="field">
      <label>Display Name</label>
      <input v-model="displayName" type="text" placeholder="Your name shown to contacts" />
    </div>

    <div class="field">
      <label>Redis URL</label>
      <input v-model="redisUrl" type="text" placeholder="redis://192.168.1.10:6379" />
    </div>

    <div class="field">
      <label>Redis Password (optional)</label>
      <input v-model="redisPassword" type="password" placeholder="Leave blank if none" />
    </div>

    <button class="save-btn" @click="save" :disabled="saving">
      {{ saving ? 'Saving…' : saved ? 'Saved ✓' : 'Save' }}
    </button>
  </div>
</template>

<style scoped>
.im-config { padding: 32px; max-width: 480px; }
h2 { font-size: 18px; font-weight: 600; margin-bottom: 20px; }
.node-id { font-size: 12px; color: var(--color-gray-500); margin-bottom: 20px; }
.node-id code { font-family: var(--font-mono); background: var(--color-gray-100); padding: 2px 4px; border-radius: 3px; }
.field { margin-bottom: 16px; }
label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; }
input {
  width: 100%; padding: 8px 12px;
  border: var(--border); border-radius: var(--radius-md);
  font-size: 14px; outline: none;
}
input:focus { border-color: var(--color-primary); }
.save-btn {
  padding: 8px 20px; background: var(--color-primary); color: white;
  border: none; border-radius: var(--radius-md); font-size: 13px;
  font-weight: 500; cursor: pointer; margin-top: 8px;
}
.save-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
