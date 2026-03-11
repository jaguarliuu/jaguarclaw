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
  display: flex; align-items: center; gap: 2px;
  padding: 5px 8px 5px 10px; cursor: pointer;
  border-radius: var(--radius-sm); margin: 0 4px;
  font-size: 13px; color: var(--color-gray-700);
  user-select: none;
}
.tree-node__row:hover { background: var(--sidebar-item-hover-bg); }
.tree-node__row.active {
  background: var(--sidebar-item-hover-bg);
  color: var(--color-primary);
  font-weight: 500;
}
.tree-node__arrow {
  font-size: 9px; color: var(--color-gray-400); width: 14px;
  cursor: pointer; flex-shrink: 0; text-align: center;
}
.tree-node__icon { font-size: 13px; flex-shrink: 0; }
.tree-node__title { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tree-node__children { padding-left: 14px; }
</style>
