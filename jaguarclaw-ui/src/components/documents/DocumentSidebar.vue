<script setup lang="ts">
import { ref } from 'vue'
import type { DocumentNode } from '@/types'
import DocTreeNode from './DocTreeNode.vue'

const props = defineProps<{
  tree: DocumentNode[]
  activeId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
  create: [parentId?: string]
  delete: [id: string]
}>()

const expandedIds = ref<Set<string>>(new Set())

function toggle(id: string) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

const contextMenu = ref<{ x: number; y: number; node: DocumentNode } | null>(null)

function showContext(e: MouseEvent, node: DocumentNode) {
  contextMenu.value = { x: e.clientX, y: e.clientY, node }
}

function closeContext() {
  contextMenu.value = null
}
</script>

<template>
  <aside class="doc-sidebar" @click="closeContext">
    <div class="doc-sidebar__header">
      <span class="doc-sidebar__title">文档</span>
      <button class="doc-sidebar__new" @click.stop="emit('create')" title="新建页面">＋</button>
    </div>

    <div class="doc-sidebar__tree">
      <DocTreeNode
        v-for="node in tree"
        :key="node.id"
        :node="node"
        :active-id="activeId"
        :expanded-ids="expandedIds"
        @select="emit('select', $event)"
        @toggle="toggle"
        @contextmenu="showContext"
        @create-child="emit('create', $event)"
      />
    </div>

    <!-- Context menu -->
    <div
      v-if="contextMenu"
      class="doc-context-menu"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      @click.stop
    >
      <button @click="emit('create', contextMenu!.node.id); closeContext()">新建子页面</button>
      <button class="danger" @click="emit('delete', contextMenu!.node.id); closeContext()">删除</button>
    </div>
  </aside>
</template>

<style scoped>
.doc-sidebar {
  width: var(--sidebar-width, 260px);
  border-right: var(--border);
  display: flex;
  flex-direction: column;
  background: var(--color-gray-50);
  overflow: hidden;
  flex-shrink: 0;
}
.doc-sidebar__header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: var(--border);
  flex-shrink: 0;
}
.doc-sidebar__title {
  font-size: 12px; font-weight: 600;
  color: var(--color-gray-500); text-transform: uppercase; letter-spacing: 0.05em;
}
.doc-sidebar__new {
  background: none; border: none; cursor: pointer;
  font-size: 18px; color: var(--color-gray-500); padding: 0 4px; line-height: 1;
}
.doc-sidebar__new:hover { color: var(--color-gray-900); }
.doc-sidebar__tree { flex: 1; overflow-y: auto; padding: var(--space-2) 0; }
.doc-context-menu {
  position: fixed; z-index: 100;
  background: var(--color-white); border: var(--border); border-radius: var(--radius-md);
  box-shadow: var(--shadow-md); padding: var(--space-1) 0; min-width: 140px;
}
.doc-context-menu button {
  display: block; width: 100%; text-align: left;
  padding: var(--space-2) var(--space-3); font-size: 13px;
  background: none; border: none; cursor: pointer;
}
.doc-context-menu button:hover { background: var(--color-gray-100); }
.doc-context-menu button.danger { color: #e53e3e; }
</style>
