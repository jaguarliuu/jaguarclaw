<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import { useChat } from '@/composables/useChat'
import { useLlmConfig } from '@/composables/useLlmConfig'
import { useContext } from '@/composables/useContext'
import { useMcpServers } from '@/composables/useMcpServers'
import { useModelSelector } from '@/composables/useModelSelector'
import { useI18n } from '@/i18n'
import { useToast } from '@/composables/useToast'
import { useDocuments } from '@/composables/useDocuments'
import type { ContextType, DocumentNode } from '@/types'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import MessageInput from '@/components/MessageInput.vue'
import SubagentPanel from '@/components/SubagentPanel.vue'
import ArtifactPanel from '@/components/ArtifactPanel.vue'
import HeartbeatDetailPanel from '@/components/HeartbeatDetailPanel.vue'
import RunOutcomeBanner from '@/components/RunOutcomeBanner.vue'
import ContextInputModal from '@/components/ContextInputModal.vue'
import DocumentSidebar from '@/components/documents/DocumentSidebar.vue'
import DocumentEditor from '@/components/documents/DocumentEditor.vue'
import DocumentAiIndicator from '@/components/documents/DocumentAiIndicator.vue'
import DocumentAiSettingsPopover from '@/components/documents/DocumentAiSettingsPopover.vue'
import ImView from '@/views/ImView.vue'
import { useArtifact } from '@/composables/useArtifact'
import { useHeartbeat } from '@/composables/useHeartbeat'

const { state: connectionState } = useWebSocket()
const { checkStatus, getConfig: loadLlmConfig, multiConfig } = useLlmConfig()
const router = useRouter()
const route = useRoute()
const { artifact } = useArtifact()
const { selectedNotification, selectNotification } = useHeartbeat()

// ─── Document mode ───────────────────────────────────────────────────────────
const isDocumentMode = computed(() => route.path.startsWith('/documents'))
const isImMode = computed(() => route.path.startsWith('/im'))
const docId = computed(() => route.params.id as string | undefined)

const {
  tree: docTree, currentDoc, saving: docSaving, aiStreaming: docAiStreaming,
  aiStatusText: docAiStatusText, docInsertContent: docAiInsertContent,
  loadTree, loadDocument, createDocument, scheduleSave, deleteDocument, aiAssist, stopAiStream,
  getConfig, setConfig,
} = useDocuments()

const showAiIndicator = ref(false)
const docEditorRef = ref<InstanceType<typeof DocumentEditor> | null>(null)
const mutableDocTree = computed(() => docTree.value as DocumentNode[])
const showAiSettings = ref(false)
const aiSystemPrompt = ref('')

watch(isDocumentMode, async (on) => {
  if (on) await loadTree()
}, { immediate: true })

watch(docId, async (id) => {
  if (isDocumentMode.value && id) await loadDocument(id)
})

async function onDocSelect(id: string) { router.push(`/documents/${id}`) }
async function onDocCreate(parentId?: string) {
  const doc = await createDocument('Untitled', parentId)
  router.push(`/documents/${doc.id}`)
}
async function onDocDelete(id: string) {
  await deleteDocument(id)
  if (docId.value === id) router.push('/documents')
}
function onDocChange(title: string, content: string, wordCount: number) {
  if (!currentDoc.value) return
  scheduleSave(currentDoc.value.id, title, content, wordCount)
}
async function onDocAiAction(action: string, selection?: string, userPrompt?: string) {
  if (!currentDoc.value) return
  showAiIndicator.value = true
  docEditorRef.value?.insertStreamingBlock()
  try {
    await aiAssist(currentDoc.value.id, action as any, selection, undefined, userPrompt, (fullContent) => {
      docEditorRef.value?.updateStreamingBlock(fullContent)
    })
  } catch (e) { console.error('AI assist failed:', e); showAiIndicator.value = false }
}
function onDocAiKeep() {
  showAiIndicator.value = false
  docEditorRef.value?.finalizeStreamingBlock(docAiInsertContent.value)
  stopAiStream()
}
async function onDocAiDiscard() {
  showAiIndicator.value = false; stopAiStream()
  docEditorRef.value?.removeStreamingBlock()
}
async function onDocAiSettings() {
  try {
    aiSystemPrompt.value = await getConfig()
    showAiSettings.value = true
  } catch (e) {
    showToast({ type: 'error', title: '加载设置失败', message: String(e) })
  }
}

async function onDocAiSettingsSave(prompt: string) {
  try {
    await setConfig(prompt)
  } catch (e) {
    showToast({ type: 'error', title: '保存设置失败', message: String(e) })
  }
}
// ─────────────────────────────────────────────────────────────────────────────
const {
  contexts: attachedContexts,
  uploadFile,
  addContext,
  removeContext,
  clearContexts,
} = useContext()
const { servers: mcpServers, loadServers: loadMcpServers } = useMcpServers()
const { selectedModel, availableModels, activeModelLabel, selectModel, supportsSelectedModelVision } = useModelSelector()
const { t } = useI18n()
const { showToast } = useToast()

// Context input modal 状态
const showContextModal = ref(false)
const currentContextType = ref<ContextType>('folder')

const {
  currentSession,
  currentSessionId,
  currentRunUsage,
  currentRunOutcome,
  messages,
  streamBlocks,
  isStreaming,
  filteredSessions,
  agents,
  selectedAgent,
  selectedAgentId,
  activeSubagentId,
  activeSubagent,
  setActiveSubagent,
  excludedMcpServers,
  toggleMcpServer,
  setSelectedAgent,
  loadSessions,
  createSession,
  selectSession,
  deleteSession,
  sendMessage,
  confirmToolCall,
  cancelRun,
  hideCurrentRunOutcome,
} = useChat()

const assistantDisplayName = computed(() => {
  const fromProfile = selectedAgent.value?.displayName || selectedAgent.value?.name
  return fromProfile?.trim() || t('message.assistant')
})

// 只显示 enabled 且已连接（有 toolCount）的 MCP 服务器
const connectedMcpServers = computed(() =>
  mcpServers.value.filter((s) => s.enabled && (s.toolCount ?? 0) > 0),
)

async function handleCreateSession() {
  const session = await createSession('New Conversation')
  await selectSession(session.id)
}

async function handleSelectSession(id: string) {
  await selectSession(id)
}

function handleDeleteSession(id: string) {
  deleteSession(id)
}

function handleSend(prompt: string, contexts: typeof attachedContexts.value) {
  const hasImageContext = contexts.some((context) => {
    if (context.type !== 'file') return false
    if (context.mimeType?.startsWith('image/')) return true
    const candidate = context.filePath || context.filename || context.displayName || ''
    return /\.(png|jpe?g|gif|webp|bmp)$/i.test(candidate)
  })

  if (hasImageContext && !supportsSelectedModelVision()) {
    showToast({
      type: 'warning',
      title: '模型不支持识图',
      message: '当前选择的模型不支持图片输入，请切换到带 Vision 标记的模型。',
      dedupeKey: 'vision-model-required'
    })
    return
  }

  // 传递上下文信息和模型选择给 sendMessage
  sendMessage(
    prompt,
    contexts.length > 0 ? contexts : undefined,
    undefined, // filePaths (legacy)
    undefined, // attachedFiles (legacy)
    selectedModel.value ?? undefined,
    selectedAgentId.value,
  )
  clearContexts()
}

function handleConfirmToolCall(callId: string, decision: 'approve' | 'reject') {
  confirmToolCall(callId, decision)
}

function handleCancel() {
  cancelRun()
}

async function handleAttachFile(file: File) {
  await uploadFile(file, {
    sessionId: currentSessionId.value,
    agentId: selectedAgentId.value,
  })
}

function handleAddContext(type: ContextType) {
  // 文件类型已经在 MessageInput 中处理，这里处理其他类型
  currentContextType.value = type
  showContextModal.value = true
}

function handleContextModalConfirm(value: string) {
  const type = currentContextType.value

  // 根据类型添加上下文
  if (type === 'folder') {
    addContext('folder', value, { folderPath: value })
  } else if (type === 'web') {
    addContext('web', value, { url: value })
  } else if (type === 'doc') {
    addContext('doc', value, { docId: value })
  } else if (type === 'code') {
    addContext('code', value, { codeSnippet: value })
  } else if (type === 'rule') {
    addContext('rule', value, { ruleContent: value })
  }

  showContextModal.value = false
}

function handleContextModalCancel() {
  showContextModal.value = false
}

function handleRemoveContext(contextId: string) {
  removeContext(contextId)
}

function handleSelectSubagent(subRunId: string) {
  // Toggle: if already selected, close panel
  if (activeSubagentId.value === subRunId) {
    setActiveSubagent(null)
  } else {
    setActiveSubagent(subRunId)
  }
}

function handleSelectModel(providerId: string, modelName: string) {
  selectModel(providerId, modelName)
}

function handleOpenModelSettings() {
  router.push('/settings/llm')
}

function handleSelectAgent(agentId: string) {
  setSelectedAgent(agentId)
}

onMounted(() => {
  // Wait for connection (managed by App.vue), then check LLM config and load sessions
  const checkConnection = setInterval(async () => {
    if (connectionState.value === 'connected') {
      clearInterval(checkConnection)

      // Check if LLM is configured — redirect to setup if not
      const status = await checkStatus()
      if (!status.llmConfigured) {
        router.replace('/setup')
        return
      }

      loadSessions()
      loadMcpServers()
      loadLlmConfig()

      // Handle install/uninstall action from system settings
      await handleInstallAction()
    }
  }, 200)
})

// Watch for query parameter changes (install/uninstall actions from system settings)
watch(
  () => route.query,
  async (newQuery) => {
    if (newQuery.action && newQuery.prompt) {
      await handleInstallAction()
    }
  },
)

async function handleInstallAction() {
  const { action, env, prompt } = route.query

  if (action && prompt && typeof prompt === 'string') {
    // Create a new session for the installation
    const sessionName = action === 'install' ? `Install ${env}` : `Uninstall ${env}`

    const session = await createSession(sessionName)
    await selectSession(session.id)

    // Send the installation prompt
    setTimeout(() => {
      sendMessage(prompt)
      // Clear query parameters
      router.replace({ path: '/workspace' })
    }, 500)
  }
}
</script>

<template>
  <div class="workspace">
    <SessionSidebar
      :sessions="filteredSessions"
      :current-id="currentSessionId"
      :agents="agents"
      :force-collapsed="isDocumentMode || isImMode"
      @select="handleSelectSession"
      @create="handleCreateSession"
      @delete="handleDeleteSession"
    />

    <!-- ─── IM mode layout ─── -->
    <template v-if="isImMode">
      <div class="im-host">
        <ImView />
      </div>
    </template>

    <!-- ─── Document mode layout ─── -->
    <template v-else-if="isDocumentMode">
      <DocumentSidebar
        :tree="mutableDocTree"
        :active-id="docId ?? null"
        @select="onDocSelect"
        @create="onDocCreate"
        @delete="onDocDelete"
      />
      <div class="main-area doc-main">
        <DocumentEditor
          ref="docEditorRef"
          :document="currentDoc"
          :saving="docSaving"
          :ai-streaming="docAiStreaming"
          @change="onDocChange"
          @ai-action="onDocAiAction"
          @ai-settings="onDocAiSettings"
        />
        <DocumentAiIndicator
          v-if="showAiIndicator"
          :streaming="docAiStreaming"
          @keep="onDocAiKeep"
          @discard="onDocAiDiscard"
        />
        <DocumentAiSettingsPopover
          v-model="showAiSettings"
          :system-prompt="aiSystemPrompt"
          @save="onDocAiSettingsSave"
        />
      </div>
    </template>

    <!-- ─── IM mode layout ─── -->
    <template v-else-if="isImMode">
      <div class="im-host">
        <ImView />
      </div>
    </template>

    <!-- ─── Chat mode layout ─── -->
    <main
      v-else
      class="main-area"
      @click="selectedNotification && !artifact ? selectNotification(null) : undefined"
    >
      <MessageList
        :messages="messages"
        :stream-blocks="streamBlocks"
        :is-streaming="isStreaming"
        :assistant-name="assistantDisplayName"
        :active-subagent-id="activeSubagentId"
        :current-session-id="currentSessionId"
        @confirm="handleConfirmToolCall"
        @select-subagent="handleSelectSubagent"
      />

      <RunOutcomeBanner
        v-if="currentRunOutcome?.visible"
        :outcome="currentRunOutcome"
        @dismiss="hideCurrentRunOutcome"
      />

      <MessageInput
        :disabled="isStreaming || connectionState !== 'connected'"
        :is-running="isStreaming"
        :attached-contexts="attachedContexts"
        :mcp-servers="connectedMcpServers"
        :excluded-mcp-servers="excludedMcpServers"
        :available-models="availableModels"
        :selected-model="selectedModel"
        :default-model="multiConfig?.defaultModel ?? ''"
        :active-model-label="activeModelLabel"
        :agents="agents"
        :selected-agent-id="selectedAgentId"
        :token-usage="currentRunUsage"
        @send="handleSend"
        @cancel="handleCancel"
        @attach-file="handleAttachFile"
        @add-context="handleAddContext"
        @remove-context="handleRemoveContext"
        @toggle-mcp-server="toggleMcpServer"
        @select-model="handleSelectModel"
        @select-agent="handleSelectAgent"
        @open-model-settings="handleOpenModelSettings"
      />
    </main>


    <ContextInputModal
      :type="currentContextType"
      :show="showContextModal"
      @confirm="handleContextModalConfirm"
      @cancel="handleContextModalCancel"
    />

    <Transition name="panel-slide">
      <SubagentPanel
        v-if="activeSubagent && !artifact && !selectedNotification"
        :subagent="activeSubagent"
        @close="setActiveSubagent(null)"
        @confirm="handleConfirmToolCall"
      />
    </Transition>

    <Transition name="panel-slide">
      <ArtifactPanel v-if="artifact" />
    </Transition>

    <Transition name="panel-slide">
      <HeartbeatDetailPanel
        v-if="selectedNotification && !artifact"
        :notification="selectedNotification"
      />
    </Transition>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  width: 100vw;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--content-bg);
  transition: all var(--duration-normal) var(--ease-out);
}

/* IM mode: flex row host so contact list and chat sit side-by-side */
.im-host {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-width: 0;
  overflow: hidden;
}

/* Document mode: editor area stacks vertically */
.doc-main {
  position: relative;
}

/* Panel slide transition */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition:
    opacity var(--duration-normal) var(--ease-out),
    transform var(--duration-normal) var(--ease-out);
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateX(40px);
}
</style>
