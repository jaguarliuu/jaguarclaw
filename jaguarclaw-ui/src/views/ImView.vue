<script setup lang="ts">
import { onMounted } from 'vue'
import { useIm } from '@/composables/useIm'
import ImContactList from '@/components/im/ImContactList.vue'
import ImChatWindow from '@/components/im/ImChatWindow.vue'
import ImPairToast from '@/components/im/ImPairToast.vue'

const {
  contacts, conversations, messages, pendingPairRequests,
  activeConversationId, settings,
  loadSettings, loadContacts, loadConversations,
  loadMessages, sendMessage,
  sendPairRequest, respondToPairRequest,
} = useIm()

onMounted(async () => {
  await loadSettings()
  await loadContacts()
  await loadConversations()
})
</script>

<template>
  <div class="im-view">
    <!-- Pair request toasts -->
    <ImPairToast
      v-for="(req, index) in pendingPairRequests"
      :key="req.fromNodeId"
      :request="req"
      :index="index"
      @accept="respondToPairRequest(req, true)"
      @reject="respondToPairRequest(req, false)"
    />

    <!-- Contact/conversation list -->
    <ImContactList
      :contacts="contacts"
      :conversations="conversations"
      :active-id="activeConversationId"
      @select="loadMessages"
      @pair-request="sendPairRequest"
    />

    <!-- Chat window -->
    <ImChatWindow
      v-if="activeConversationId"
      :conversation-id="activeConversationId"
      :messages="messages[activeConversationId] ?? []"
      :self-node-id="settings?.nodeId ?? ''"
      @send="(text) => sendMessage(activeConversationId!, text)"
    />

    <div v-else class="im-empty">
      <p>Select a conversation or pair with a new contact</p>
    </div>
  </div>
</template>

<style scoped>
.im-view {
  display: flex;
  height: 100%;
  background: var(--content-bg);
  position: relative;
}
.im-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-gray-400);
  font-size: 14px;
}
</style>
