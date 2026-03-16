<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useIm } from '@/composables/useIm'
import { useI18n } from '@/i18n'
import ImContactList from '@/components/im/ImContactList.vue'
import ImChatWindow from '@/components/im/ImChatWindow.vue'

const { t } = useI18n()
const {
  contacts, conversations, messages,
  activeConversationId, settings,
  loadSettings, loadContacts, loadConversations,
  loadMessages, sendMessage, sendFile, startChat, clearConversation,
} = useIm()

const activeConversation = computed(() =>
  conversations.value.find(c => c.id === activeConversationId.value)
)

const activeContact = computed(() =>
  contacts.value.find(c => c.nodeId === activeConversationId.value)
)

onMounted(async () => {
  await loadSettings()
  await loadContacts()
  await loadConversations()
})
</script>

<template>
  <div class="im-view">
    <!-- Contact/conversation list -->
    <ImContactList
      :contacts="contacts"
      :conversations="conversations"
      :active-id="activeConversationId"
      @select="loadMessages"
      @start-chat="startChat"
    />

    <!-- Chat window -->
    <ImChatWindow
      v-if="activeConversationId"
      :conversation-id="activeConversationId"
      :messages="messages[activeConversationId] ?? []"
      :self-node-id="settings?.nodeId ?? ''"
      :contact-name="activeConversation?.displayName"
      :contact-avatar-style="activeContact?.avatarStyle"
      :contact-avatar-seed="activeContact?.avatarSeed"
      :self-avatar-style="settings?.avatarStyle"
      :self-avatar-seed="settings?.avatarSeed"
      @send="(text) => sendMessage(activeConversationId!, text)"
      @send-file="(file) => sendFile(activeConversationId!, file)"
      @clear-chat="() => clearConversation(activeConversationId!)"
    />

    <!-- Empty state -->
    <div v-else class="im-empty">
      <div class="empty-ring">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
          <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
            stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <p class="empty-text">{{ t('im.selectConversation') }}</p>
    </div>
  </div>
</template>

<style scoped>
.im-view {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-width: 0;
  overflow: hidden;
}
.im-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--color-gray-50);
}
.empty-ring {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  background: var(--color-gray-200);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-gray-500);
}
.empty-text {
  font-size: 13px;
  color: var(--color-gray-400);
}
</style>
