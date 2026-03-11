<!-- src/views/DocumentView.vue -->
<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useDocuments } from '@/composables/useDocuments'
import type { DocumentNode } from '@/types'
import DocumentSidebar from '@/components/documents/DocumentSidebar.vue'
import DocumentEditor from '@/components/documents/DocumentEditor.vue'
import DocumentAiIndicator from '@/components/documents/DocumentAiIndicator.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()

const {
  tree, currentDoc, saving, aiStreaming, docInsertContent,
  loadTree, loadDocument, createDocument, scheduleSave, deleteDocument,
  aiAssist, stopAiStream,
} = useDocuments()

const showAiIndicator = ref(false)
const editorRef = ref<InstanceType<typeof DocumentEditor> | null>(null)
// Cast readonly tree from composable to mutable for child component prop
const mutableTree = computed(() => tree.value as DocumentNode[])

onMounted(async () => {
  await loadTree()
  if (props.id) await loadDocument(props.id)
})

watch(() => props.id, async (id) => {
  if (id) await loadDocument(id)
  else {
    // No document selected — clear current
    // currentDoc is readonly, selection driven by route
  }
})

async function onSelect(id: string) {
  router.push(`/documents/${id}`)
}

async function onCreate(parentId?: string) {
  const doc = await createDocument('Untitled', parentId)
  router.push(`/documents/${doc.id}`)
}

async function onDelete(id: string) {
  await deleteDocument(id)
  if (props.id === id) router.push('/documents')
}

function onChange(title: string, content: string, wordCount: number) {
  if (!currentDoc.value) return
  scheduleSave(currentDoc.value.id, title, content, wordCount)
}

async function onAiAction(action: string, selection?: string) {
  if (!currentDoc.value) return
  showAiIndicator.value = true
  editorRef.value?.insertStreamingBlock()
  try {
    await aiAssist(currentDoc.value.id, action as any, selection, undefined, undefined, (fullContent) => {
      editorRef.value?.updateStreamingBlock(fullContent)
    })
  } catch (e) {
    console.error('AI assist failed:', e)
    showAiIndicator.value = false
  }
}

function onAiKeep() {
  showAiIndicator.value = false
  editorRef.value?.finalizeStreamingBlock(docInsertContent.value)
  stopAiStream()
}

async function onAiDiscard() {
  showAiIndicator.value = false
  editorRef.value?.removeStreamingBlock()
  stopAiStream()
  // Reload from server to discard AI-inserted content
  if (currentDoc.value) await loadDocument(currentDoc.value.id)
}
</script>

<template>
  <div class="document-view">
    <DocumentSidebar
      :tree="mutableTree"
      :active-id="id ?? null"
      @select="onSelect"
      @create="onCreate"
      @delete="onDelete"
    />
    <div class="document-view__main">
      <DocumentEditor
        ref="editorRef"
        :document="currentDoc"
        :saving="saving"
        :ai-streaming="aiStreaming"
        @change="onChange"
        @ai-action="onAiAction"
      />
      <DocumentAiIndicator
        v-if="showAiIndicator"
        :streaming="aiStreaming"
        @keep="onAiKeep"
        @discard="onAiDiscard"
      />
    </div>
  </div>
</template>

<style scoped>
.document-view {
  display: flex; height: 100%; overflow: hidden;
}
.document-view__main {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
</style>
