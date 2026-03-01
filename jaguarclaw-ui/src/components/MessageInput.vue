<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import type {
  SlashCommandItem,
  AttachedContext,
  ContextType,
  DataSourceInfo,
  ModelOption,
  AgentProfile,
} from '@/types'
import type { McpServer } from '@/composables/useMcpServers'
import { useSlashCommands } from '@/composables/useSlashCommands'
import { useI18n } from '@/i18n'
import ContextChip from '@/components/ContextChip.vue'
import ContextTypeMenu from '@/components/ContextTypeMenu.vue'
import McpServerFilter from '@/components/McpServerFilter.vue'
import DataSourceSelector from '@/components/DataSourceSelector.vue'
import ModelSelector from '@/components/ModelSelector.vue'

const props = defineProps<{
  disabled: boolean
  isRunning?: boolean
  attachedContexts?: AttachedContext[]
  mcpServers?: McpServer[]
  excludedMcpServers?: Set<string>
  dataSources?: readonly DataSourceInfo[]
  selectedDataSourceId?: string
  availableModels?: ModelOption[]
  selectedModel?: string | null
  defaultModel?: string
  activeModelLabel?: string
  agents?: AgentProfile[]
  selectedAgentId?: string
}>()

const emit = defineEmits<{
  send: [message: string, contexts: AttachedContext[]]
  cancel: []
  'add-context': [type: ContextType]
  'attach-file': [file: File]
  'remove-context': [contextId: string]
  'toggle-mcp-server': [serverName: string]
  'select-datasource': [dataSourceId: string | undefined]
  'select-model': [providerId: string, modelName: string]
  'select-agent': [agentId: string]
  'open-model-settings': []
}>()

const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const isExpanded = ref(false)

// Slash command autocomplete
const { loadCommands, filterCommands } = useSlashCommands()
const { t } = useI18n()

const showSlashMenu = ref(false)
const slashItems = ref<SlashCommandItem[]>([])
const selectedIndex = ref(0)
const menuRef = ref<HTMLElement | null>(null)

// Context type menu
const showContextMenu = ref(false)
const contextMenuRef = ref<InstanceType<typeof ContextTypeMenu> | null>(null)

// MCP server filter
const showMcpFilter = ref(false)

// Data source selector
const showDataSourceSelector = ref(false)

// Model selector
const showModelSelector = ref(false)
const showAgentSelector = ref(false)

// 是否显示模型选择按钮
const showModelButton = computed(() => {
  return (props.availableModels?.length ?? 0) > 0
})

// 模型标签
const modelLabel = computed(() => {
  return props.activeModelLabel || 'Model'
})

const enabledAgents = computed(() => (props.agents ?? []).filter((agent) => agent.enabled))

const showAgentButton = computed(() => enabledAgents.value.length > 0)

const selectedAgent = computed(() => {
  const targetId = props.selectedAgentId
  if (!targetId) return enabledAgents.value[0] ?? null
  return (
    enabledAgents.value.find((agent) => agent.id === targetId) ?? enabledAgents.value[0] ?? null
  )
})

const agentLabel = computed(
  () => selectedAgent.value?.displayName || selectedAgent.value?.name || t('input.agentFallback'),
)

// MCP 状态标签
const mcpStatusLabel = computed(() => {
  const servers = props.mcpServers ?? []
  if (servers.length === 0) return null
  const excluded = props.excludedMcpServers?.size ?? 0
  const active = servers.length - excluded
  return `MCP: ${active}/${servers.length}`
})

// 数据源状态标签
const dataSourceLabel = computed(() => {
  const sources = props.dataSources ?? []
  const activeSources = sources.filter((s) => s.status === 'ACTIVE')
  if (activeSources.length === 0) return null

  const selectedSource = activeSources.find((s) => s.id === props.selectedDataSourceId)
  if (selectedSource) {
    return selectedSource.name
  }
  return '数据源'
})

// 获取选中的数据源对象
const selectedDataSource = computed(() => {
  if (!props.selectedDataSourceId) return null
  return props.dataSources?.find((ds) => ds.id === props.selectedDataSourceId)
})

// 是否显示 MCP 按钮
const showMcpButton = computed(() => {
  const servers = props.mcpServers ?? []
  return servers.length > 0
})

// 是否显示数据源按钮
const showDataSourceButton = computed(() => {
  const sources = props.dataSources ?? []
  return sources.filter((s) => s.status === 'ACTIVE').length > 0
})

// 是否有上下文正在上传
const hasUploading = computed(() => props.attachedContexts?.some((c) => c.uploading) ?? false)

// 发送按钮是否禁用：常规禁用 OR 有上下文正在上传
const sendDisabled = computed(() => props.disabled || hasUploading.value || !input.value.trim())

onMounted(() => {
  loadCommands()
})

function scrollSelectedIntoView() {
  nextTick(() => {
    const menu = menuRef.value
    if (!menu) return
    const selected = menu.children[selectedIndex.value] as HTMLElement | undefined
    if (selected) {
      selected.scrollIntoView({ block: 'nearest' })
    }
  })
}

function selectCommand(item: SlashCommandItem) {
  const prefix = item.type === 'agent' ? '@' : '/'
  input.value = prefix + item.name + ' '
  showSlashMenu.value = false
  inputRef.value?.focus()
}

function handleSubmit() {
  if (sendDisabled.value) return

  // 收集所有上下文
  const contexts = props.attachedContexts || []

  emit('send', input.value.trim(), contexts)
  input.value = ''

  // Reset textarea height
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }
}

function handleAttachClick() {
  showContextMenu.value = !showContextMenu.value
}

function handleContextTypeSelect(type: ContextType) {
  showContextMenu.value = false

  if (type === 'file') {
    // 文件类型触发文件选择器
    fileInputRef.value?.click()
  } else {
    // 其他类型通知父组件
    emit('add-context', type)
  }
}

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    emit('attach-file', file)
  }
  // Reset so same file can be selected again
  target.value = ''
}

function handleCancel() {
  emit('cancel')
}

function handleKeydown(e: KeyboardEvent) {
  // Context type menu keyboard navigation
  if (showContextMenu.value) {
    if (e.key === 'Escape') {
      e.preventDefault()
      showContextMenu.value = false
      return
    }
    // 将键盘事件转发给 ContextTypeMenu
    contextMenuRef.value?.handleKeydown(e)
    return
  }

  // Slash menu keyboard navigation
  if (showSlashMenu.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIndex.value = (selectedIndex.value + 1) % slashItems.value.length
      scrollSelectedIntoView()
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIndex.value =
        (selectedIndex.value - 1 + slashItems.value.length) % slashItems.value.length
      scrollSelectedIntoView()
      return
    }
    if ((e.key === 'Tab' || e.key === 'Enter') && !e.isComposing) {
      e.preventDefault()
      const item = slashItems.value[selectedIndex.value]
      if (item) selectCommand(item)
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      showSlashMenu.value = false
      return
    }
  }

  // Enter to submit (Shift+Enter for new line)
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSubmit()
  }
  // Escape to cancel
  if (e.key === 'Escape' && props.isRunning) {
    handleCancel()
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  // Auto-resize
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 200) + 'px'

  const val = target.value

  // # shortcut: open context type menu
  if (val === '#') {
    input.value = ''
    showContextMenu.value = true
    return
  }

  // Slash command detection
  if (val.startsWith('/')) {
    const query = val.substring(1).split(/\s/)[0] ?? ''
    if (!val.includes(' ')) {
      slashItems.value = filterCommands(query)
      showSlashMenu.value = slashItems.value.length > 0
      selectedIndex.value = 0
      return
    }
  }

  // @ mention detection（仅在输入首位触发）
  if (val.startsWith('@')) {
    const query = val.substring(1).split(/\s/)[0] ?? ''
    if (!val.includes(' ')) {
      const q = query.toLowerCase()
      slashItems.value = enabledAgents.value
        .map((agent) => ({
          type: 'agent' as const,
          name: agent.id,
          description: agent.description || agent.displayName || agent.name,
          displayName: '@' + agent.id,
        }))
        .filter((item) => !query || item.name.toLowerCase().includes(q) || item.description.toLowerCase().includes(q))
      showSlashMenu.value = slashItems.value.length > 0
      selectedIndex.value = 0
      return
    }
  }
  showSlashMenu.value = false
}

// 点击外部关闭上下文菜单、MCP 过滤器和数据源选择器
function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement

  // 关闭上下文菜单
  if (showContextMenu.value) {
    const menuEl = contextMenuRef.value?.$el
    const attachBtn = target.closest('.toolbar-btn')
    if (menuEl && !menuEl.contains(target) && !attachBtn) {
      showContextMenu.value = false
    }
  }

  // 关闭 MCP 过滤器
  if (showMcpFilter.value) {
    const filterEl = document.querySelector('.mcp-filter')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (filterEl && !filterEl.contains(target) && !toolbarBtn) {
      showMcpFilter.value = false
    }
  }

  // 关闭数据源选择器
  if (showDataSourceSelector.value) {
    const selectorEl = document.querySelector('.datasource-selector')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (selectorEl && !selectorEl.contains(target) && !toolbarBtn) {
      showDataSourceSelector.value = false
    }
  }

  // 关闭模型选择器
  if (showModelSelector.value) {
    const selectorEl = document.querySelector('.model-selector')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (selectorEl && !selectorEl.contains(target) && !toolbarBtn) {
      showModelSelector.value = false
    }
  }

  // 关闭 Agent 选择器
  if (showAgentSelector.value) {
    const selectorEl = document.querySelector('.agent-selector')
    const toolbarBtn = target.closest('.toolbar-btn')
    if (selectorEl && !selectorEl.contains(target) && !toolbarBtn) {
      showAgentSelector.value = false
    }
  }
}

function handleRemoveDataSource() {
  emit('select-datasource', undefined)
}

function toggleModelSelector() {
  showModelSelector.value = !showModelSelector.value
  if (showModelSelector.value) {
    showAgentSelector.value = false
  }
}

function toggleAgentSelector() {
  showAgentSelector.value = !showAgentSelector.value
  if (showAgentSelector.value) {
    showModelSelector.value = false
  }
}

function handleSelectAgent(agentId: string) {
  emit('select-agent', agentId)
  showAgentSelector.value = false
}

const isHovered = ref(false)
const isFocused = ref(false)
const isDragging = ref(false)
let dragDepth = 0

function handleFocus() {
  isFocused.value = true
  isExpanded.value = true
}

function handleMouseEnter() {
  isHovered.value = true
}

function handleMouseLeave() {
  isHovered.value = false
  // 如果没有焦点且没有内容，延迟收起
  setTimeout(() => {
    if (
      !isHovered.value &&
      !isFocused.value &&
      !input.value &&
      !props.attachedContexts?.length &&
      !props.selectedDataSourceId
    ) {
      isExpanded.value = false
    }
  }, 100)
}

function handleBlur() {
  isFocused.value = false
  // 延迟收起，避免点击按钮时立即收起
  setTimeout(() => {
    if (
      !isHovered.value &&
      !input.value &&
      !props.attachedContexts?.length &&
      !props.selectedDataSourceId
    ) {
      isExpanded.value = false
    }
  }, 200)
}

function handleDragEnter(e: DragEvent) {
  e.preventDefault()
  dragDepth++
  if (e.dataTransfer?.types.includes('Files')) {
    isDragging.value = true
  }
}

function handleDragLeave(e: DragEvent) {
  e.preventDefault()
  dragDepth--
  if (dragDepth === 0) {
    isDragging.value = false
  }
}

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy'
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  dragDepth = 0
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    emit('attach-file', file)
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleClickOutside)
})
</script>

<template>
  <div class="input-area">
    <div class="input-container">
      <!-- Slash command dropdown (positioned above input) -->
      <div v-if="showSlashMenu" ref="menuRef" class="slash-menu">
        <div
          v-for="(item, i) in slashItems"
          :key="item.name"
          class="slash-item"
          :class="{ selected: i === selectedIndex }"
          @mousedown.prevent="selectCommand(item)"
          @mouseenter="selectedIndex = i"
        >
          <span class="slash-item-name">{{ item.displayName }}</span>
          <span class="slash-item-type">{{ item.type }}</span>
          <span class="slash-item-desc">{{ item.description }}</span>
        </div>
      </div>

      <!-- Context type menu -->
      <ContextTypeMenu
        v-if="showContextMenu"
        ref="contextMenuRef"
        @select="handleContextTypeSelect"
      />

      <!-- MCP server filter -->
      <McpServerFilter
        v-if="showMcpFilter"
        :servers="mcpServers ?? []"
        :excluded-servers="excludedMcpServers ?? new Set()"
        @toggle="emit('toggle-mcp-server', $event)"
      />

      <!-- Data source selector -->
      <DataSourceSelector
        v-if="showDataSourceSelector"
        :data-sources="dataSources ?? []"
        :selected-data-source-id="selectedDataSourceId"
        @select="emit('select-datasource', $event)"
      />

      <!-- Model selector -->
      <ModelSelector
        v-if="showModelSelector"
        :available-models="availableModels ?? []"
        :selected-model="selectedModel ?? null"
        :default-model="defaultModel ?? ''"
        @select="(pid: string, mname: string) => emit('select-model', pid, mname)"
        @open-settings="emit('open-model-settings')"
      />

      <!-- Agent selector -->
      <div v-if="showAgentSelector" class="agent-selector">
        <div class="agent-header">
          <span class="agent-title">{{ t('input.tooltipAgent') }}</span>
          <span class="agent-count">{{ enabledAgents.length }}</span>
        </div>
        <div v-if="enabledAgents.length === 0" class="agent-empty">
          {{ t('input.noAgent') }}
        </div>
        <div v-else class="agent-list">
          <button
            v-for="agent in enabledAgents"
            :key="agent.id"
            class="agent-item"
            :class="{ selected: agent.id === selectedAgentId }"
            @click="handleSelectAgent(agent.id)"
          >
            <span class="agent-name">{{ agent.displayName || agent.name }}</span>
            <span class="agent-id">@{{ agent.id }}</span>
          </button>
        </div>
      </div>

      <!-- Context attachment chips -->
      <div v-if="attachedContexts && attachedContexts.length > 0" class="attached-contexts">
        <ContextChip
          v-for="context in attachedContexts"
          :key="context.id"
          :context="context"
          @remove="emit('remove-context', $event)"
        />
      </div>

      <div
        class="input-wrap"
        :class="{
          expanded:
            isExpanded ||
            input.length > 0 ||
            (attachedContexts && attachedContexts.length > 0) ||
            selectedDataSource,
          dragging: isDragging,
        }"
        @mouseenter="handleMouseEnter"
        @mouseleave="handleMouseLeave"
        @dragenter="handleDragEnter"
        @dragleave="handleDragLeave"
        @dragover="handleDragOver"
        @drop="handleDrop"
      >
        <!-- Drag & drop overlay -->
        <Transition name="drag-fade">
          <div v-if="isDragging" class="drag-overlay">
            <svg
              width="28"
              height="28"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" />
            </svg>
            <span>{{ t('input.dragDrop') }}</span>
          </div>
        </Transition>
        <!-- Hidden file input -->
        <input
          ref="fileInputRef"
          type="file"
          accept=".pdf,.docx,.txt,.md,.xlsx,.pptx,.csv,.json,.yaml,.yml,.xml,.html"
          style="display: none"
          @change="handleFileChange"
        />

        <!-- Main content area -->
        <div class="input-main">
          <!-- Chips container -->
          <div
            v-if="selectedDataSource || (attachedContexts && attachedContexts.length > 0)"
            class="chips-container"
          >
            <!-- Selected datasource chip -->
            <div v-if="selectedDataSource" class="chip datasource-chip">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none" class="chip-icon">
                <path
                  d="M2 3C2 2.44772 2.44772 2 3 2H9C9.55228 2 10 2.44772 10 3V4C10 4.55228 9.55228 5 9 5H3C2.44772 5 2 4.55228 2 4V3Z"
                  fill="currentColor"
                />
                <path
                  d="M2 8C2 7.44772 2.44772 7 3 7H9C9.55228 7 10 7.44772 10 8V9C10 9.55228 9.55228 10 9 10H3C2.44772 10 2 9.55228 2 9V8Z"
                  fill="currentColor"
                />
              </svg>
              <span class="chip-label">{{ selectedDataSource.name }}</span>
              <button
                class="chip-remove"
                @click="handleRemoveDataSource"
                :title="t('common.remove')"
              >
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <path
                    d="M3 3L9 9M9 3L3 9"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                  />
                </svg>
              </button>
            </div>

            <!-- Context chips -->
            <ContextChip
              v-for="context in attachedContexts"
              :key="context.id"
              :context="context"
              @remove="emit('remove-context', $event)"
            />
          </div>

          <!-- Textarea -->
          <textarea
            ref="inputRef"
            v-model="input"
            :disabled="disabled"
            :placeholder="
              selectedDataSource
                ? t('input.placeholderDs', { name: selectedDataSource.name })
                : t('input.placeholder')
            "
            @keydown="handleKeydown"
            @input="handleInput"
            @focus="handleFocus"
            @blur="handleBlur"
          ></textarea>

          <!-- Bottom toolbar -->
          <div class="input-toolbar">
            <div class="toolbar-left">
              <button
                class="toolbar-btn"
                :class="{ active: showContextMenu }"
                :disabled="disabled"
                @click="handleAttachClick"
                :title="t('input.tooltipAttach')"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M8 3V13M3 8H13"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                  />
                </svg>
              </button>

              <button
                v-if="showMcpButton"
                class="toolbar-btn"
                :class="{ active: showMcpFilter, highlight: (excludedMcpServers?.size ?? 0) > 0 }"
                :disabled="disabled"
                @click="showMcpFilter = !showMcpFilter"
                :title="t('input.tooltipMcp')"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M11 5L8 2L5 5M5 11L8 14L11 11M14 8H2"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>

              <button
                v-if="showDataSourceButton"
                class="toolbar-btn"
                :class="{ active: showDataSourceSelector, highlight: selectedDataSourceId }"
                :disabled="disabled"
                @click="showDataSourceSelector = !showDataSourceSelector"
                :title="t('input.tooltipDataSource')"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M2 4C2 3.44772 3.34315 3 5 3H11C12.6569 3 14 3.44772 14 4V5.5C14 6.05228 12.6569 6.5 11 6.5H5C3.34315 6.5 2 6.05228 2 5.5V4Z"
                    stroke="currentColor"
                    stroke-width="1.2"
                  />
                  <path
                    d="M2 10.5C2 9.94772 3.34315 9.5 5 9.5H11C12.6569 9.5 14 9.94772 14 10.5V12C14 12.5523 12.6569 13 11 13H5C3.34315 13 2 12.5523 2 12V10.5Z"
                    stroke="currentColor"
                    stroke-width="1.2"
                  />
                </svg>
              </button>
            </div>

            <div class="toolbar-right">
              <!-- Agent selector button -->
              <button
                v-if="showAgentButton"
                class="toolbar-btn model-btn agent-btn"
                :class="{ active: showAgentSelector }"
                :disabled="disabled"
                @click="toggleAgentSelector"
                :title="t('input.tooltipAgent')"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <circle cx="8" cy="5" r="2.25" stroke="currentColor" stroke-width="1.4" />
                  <path
                    d="M3.5 13C3.5 10.7909 5.29086 9 7.5 9H8.5C10.7091 9 12.5 10.7909 12.5 13"
                    stroke="currentColor"
                    stroke-width="1.4"
                    stroke-linecap="round"
                  />
                </svg>
                <span class="toolbar-label model-label">{{ agentLabel }}</span>
              </button>

              <!-- Model selector button -->
              <button
                v-if="showModelButton"
                class="toolbar-btn model-btn"
                :class="{ active: showModelSelector }"
                :disabled="disabled"
                @click="toggleModelSelector"
                :title="t('input.tooltipModel')"
              >
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  <path
                    d="M4 6L8 10L12 6"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                <span class="toolbar-label model-label">{{ modelLabel }}</span>
              </button>

              <!-- Send or Cancel button -->
              <button
                v-if="isRunning"
                class="action-btn cancel-btn"
                @click="handleCancel"
                :title="t('input.tooltipStop')"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <rect x="5" y="5" width="6" height="6" rx="0.5" fill="currentColor" />
                </svg>
              </button>

              <button
                v-else
                class="action-btn send-btn"
                :disabled="sendDisabled"
                @click="handleSubmit"
                :title="hasUploading ? t('input.tooltipWaitUpload') : t('input.tooltipSend')"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path
                    d="M8 13V3M8 3L5 6M8 3L11 6"
                    stroke="currentColor"
                    stroke-width="1.8"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="input-hint">
      <template v-if="hasUploading">
        <span class="uploading-hint">{{ t('input.hintUploading') }}</span>
      </template>
      <template v-else-if="isRunning">
        <span class="running">{{ t('input.hintRunning') }}</span>
        <span class="separator">·</span>
        <span>{{ t('input.hintStop') }}</span>
      </template>
      <template v-else>
        <span>{{ t('input.hintSend') }}</span>
        <span class="separator">·</span>
        <span>{{ t('input.hintNewline') }}</span>
        <span class="separator">·</span>
        <span>{{ t('input.hintFile') }}</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.input-area {
  padding: 10px 32px 18px;
  border-top: 1px solid var(--sidebar-panel-border);
  background: var(--content-bg);
}

.input-container {
  max-width: 900px;
  margin: 0 auto;
  position: relative;
}

.input-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1.5px solid var(--sidebar-panel-border);
  border-radius: 14px;
  background: var(--sidebar-panel-bg);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.06),
    0 4px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.input-wrap:hover {
  border-color: var(--color-gray-300);
  box-shadow:
    0 2px 6px rgba(0, 0, 0, 0.06),
    0 6px 16px rgba(0, 0, 0, 0.05);
}

.input-wrap.expanded,
.input-wrap:focus-within {
  border-color: var(--color-primary);
  box-shadow:
    0 2px 6px rgba(0, 0, 0, 0.06),
    0 8px 20px rgba(0, 0, 0, 0.06),
    0 0 0 3px rgba(var(--color-primary-rgb), 0.08);
}

/* Main input area */
.input-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 12px 14px 4px;
  gap: 6px;
  min-height: 42px;
  transition: min-height 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-wrap.expanded .input-main,
.input-wrap:focus-within .input-main {
  min-height: 68px;
}

/* Chips container */
.chips-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  animation: slideDown 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  padding-bottom: 4px;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(135deg, var(--color-gray-100) 0%, var(--color-gray-50) 100%);
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-gray-800);
  transition: all 0.15s ease;
  animation: chipIn 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.chip:hover {
  background: linear-gradient(135deg, var(--color-gray-200) 0%, var(--color-gray-100) 100%);
  border-color: var(--color-gray-300);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.chip-icon {
  color: var(--color-gray-600);
  flex-shrink: 0;
}

.chip-label {
  font-weight: 500;
  line-height: 1.2;
}

.chip-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-gray-500);
  cursor: pointer;
  transition: all 0.12s ease;
}

.chip-remove:hover {
  background: var(--color-gray-300);
  color: var(--color-gray-800);
  transform: scale(1.15);
}

/* Textarea */
textarea {
  flex: 1;
  width: 100%;
  min-height: 22px;
  padding: 0;
  border: none;
  background: transparent;
  font-family: var(--font-ui);
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-black);
  resize: none;
  outline: none;
  font-weight: 400;
}

textarea::placeholder {
  color: var(--color-gray-400);
  font-weight: 400;
}

textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Bottom toolbar — seamless, no separator */
.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px 8px;
  background: transparent;
  margin: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 7px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-gray-400);
  font-family: var(--font-ui);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
}

.toolbar-btn:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-gray-700);
}

.toolbar-btn.active {
  background: rgba(var(--color-primary-rgb), 0.12);
  color: var(--color-primary);
}

.toolbar-btn.highlight {
  background: rgba(var(--color-primary-rgb), 0.12);
  color: var(--color-primary);
}

.toolbar-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.toolbar-btn svg {
  flex-shrink: 0;
}

.model-btn {
  padding: 0 8px;
  gap: 4px;
  font-size: 11px;
  font-family: var(--font-mono);
  letter-spacing: -0.01em;
}

.agent-btn {
  max-width: 180px;
}

.toolbar-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Action buttons (Send/Cancel) */
.action-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.send-btn {
  background: var(--color-primary);
  color: var(--color-white);
  box-shadow: 0 2px 8px rgba(var(--color-primary-rgb), 0.25);
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(var(--color-primary-rgb), 0.35);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}

.send-btn:disabled {
  background: var(--color-gray-200);
  color: var(--color-gray-400);
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

.cancel-btn {
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-gray-600);
}

.cancel-btn:hover {
  background: rgba(0, 0, 0, 0.1);
  color: var(--color-gray-700);
  transform: translateY(-1px);
}

.attached-contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  animation: slideDown 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-hint {
  max-width: 900px;
  margin: 5px auto 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
  display: flex;
  gap: 8px;
  justify-content: center;
}

.separator {
  opacity: 0.5;
}

.running {
  animation: pulse 1.5s ease-in-out infinite;
}

.uploading-hint {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}

/* Slash command autocomplete menu */
.slash-menu {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 240px;
  overflow-y: auto;
  background: var(--color-white);
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  padding: 4px;
}

.slash-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 12px;
  border-radius: var(--radius-md);
}

.slash-item.selected {
  background: var(--color-gray-50);
}

.slash-item-name {
  font-weight: 600;
  min-width: 120px;
}

.slash-item-type {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 1px 5px;
  background: var(--color-gray-100);
  border-radius: var(--radius-sm);
  color: var(--color-gray-500);
}

.slash-item-desc {
  color: var(--color-gray-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-selector {
  position: absolute;
  bottom: calc(100% + 8px);
  right: 0;
  width: 280px;
  border: var(--border-strong);
  border-radius: var(--radius-lg);
  background: var(--color-white);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 100;
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: var(--border);
  background: var(--color-gray-50);
}

.agent-title {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-600);
}

.agent-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

.agent-empty {
  padding: 18px 12px;
  text-align: center;
  color: var(--color-gray-500);
  font-size: 12px;
}

.agent-list {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
}

.agent-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-in-out);
}

.agent-item:hover {
  background: var(--color-gray-50);
}

.agent-item.selected {
  background: var(--color-gray-100);
}

.agent-name {
  font-family: var(--font-ui);
  font-size: 13px;
  color: var(--color-black);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-id {
  flex-shrink: 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-gray-400);
}

/* Drag & drop overlay */
.drag-overlay {
  position: absolute;
  inset: 0;
  background: rgba(var(--color-primary-rgb), 0.07);
  border: 2px dashed var(--color-primary);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 10;
  pointer-events: none;
  color: var(--color-primary);
  font-family: var(--font-ui);
  font-size: 13px;
  font-weight: 500;
}

.input-wrap.dragging {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(var(--color-primary-rgb), 0.12);
}

.drag-fade-enter-active,
.drag-fade-leave-active {
  transition: opacity 0.15s ease;
}

.drag-fade-enter-from,
.drag-fade-leave-to {
  opacity: 0;
}
</style>
