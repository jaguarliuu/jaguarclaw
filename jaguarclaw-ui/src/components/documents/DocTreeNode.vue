<script setup lang="ts">
import type { DocumentNode } from '@/types'

const props = defineProps<{
  node: DocumentNode
  activeId: string | null
  expandedIds: Set<string>
}>()

const emit = defineEmits<{
  select: [id: string]
  toggle: [id: string]
  contextmenu: [e: MouseEvent, node: DocumentNode]
  'create-child': [parentId: string]
}>()

function handleChildContextMenu(e: MouseEvent, node: DocumentNode) {
  emit('contextmenu', e, node)
}
</script>

<template>
  <div class="tree-node">
    <div
      class="tree-node__row"
      :class="{ active: node.id === activeId }"
      @click="$emit('select', node.id)"
      @contextmenu.prevent="$emit('contextmenu', $event, node)"
    >
      <span
        class="tree-node__arrow"
        :style="{ opacity: node.children.length ? 1 : 0 }"
        @click.stop="$emit('toggle', node.id)"
      >{{ expandedIds.has(node.id) ? '▾' : '▸' }}</span>
      <span class="tree-node__icon">📄</span>
      <span class="tree-node__title">{{ node.title }}</span>
    </div>
    <div v-if="expandedIds.has(node.id) && node.children.length" class="tree-node__children">
      <DocTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :active-id="activeId"
        :expanded-ids="expandedIds"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
        @contextmenu="handleChildContextMenu"
        @create-child="$emit('create-child', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.tree-node__row {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 8px; cursor: pointer; border-radius: var(--radius-sm);
  font-size: 13px; color: var(--color-gray-700);
}
.tree-node__row:hover { background: var(--color-gray-100); }
.tree-node__row.active { background: var(--color-gray-200); font-weight: 500; }
.tree-node__arrow { font-size: 10px; color: var(--color-gray-400); width: 12px; cursor: pointer; }
.tree-node__icon { font-size: 12px; }
.tree-node__title { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tree-node__children { padding-left: 16px; }
</style>
