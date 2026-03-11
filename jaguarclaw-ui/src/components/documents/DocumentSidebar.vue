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
  width: 220px;
  border-right: var(--border);
  display: flex;
  flex-direction: column;
  background: var(--sidebar-panel-bg);
  overflow: hidden;
  flex-shrink: 0;
}
.doc-sidebar__header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 12px 10px 16px;
  border-bottom: var(--border);
  flex-shrink: 0;
}
.doc-sidebar__title {
  font-size: 13px; font-weight: 600;
  color: var(--color-gray-800);
}
.doc-sidebar__new {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  background: none; border: none; cursor: pointer;
  color: var(--color-gray-500); border-radius: var(--radius-sm);
  font-size: 16px; line-height: 1;
}
.doc-sidebar__new:hover { background: var(--sidebar-item-hover-bg); color: var(--color-gray-900); }
.doc-sidebar__tree { flex: 1; overflow-y: auto; padding: 6px 0; }
.doc-context-menu {
  position: fixed; z-index: 200;
  background: var(--color-white); border: var(--border); border-radius: var(--radius-md);
  box-shadow: var(--shadow-md); padding: 4px 0; min-width: 140px;
}
.doc-context-menu button {
  display: block; width: 100%; text-align: left;
  padding: 6px 12px; font-size: 13px; font-family: var(--font-ui);
  background: none; border: none; cursor: pointer; color: var(--color-gray-700);
}
.doc-context-menu button:hover { background: var(--color-gray-100); }
.doc-context-menu button.danger { color: var(--color-error); }
</style>
