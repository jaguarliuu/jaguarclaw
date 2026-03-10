<script setup lang="ts">
import { ref, watch } from 'vue'

export interface SlashMenuItem {
  label: string
  description: string
  icon: string
  action: () => void
}

const props = defineProps<{
  items: SlashMenuItem[]
}>()

const selectedIndex = ref(0)

watch(() => props.items, () => { selectedIndex.value = 0 })

function selectItem(index: number) {
  props.items[index]?.action()
}

function onKeyDown(event: KeyboardEvent): boolean {
  if (event.key === 'ArrowDown') {
    selectedIndex.value = (selectedIndex.value + 1) % Math.max(props.items.length, 1)
    event.preventDefault()
    return true
  }
  if (event.key === 'ArrowUp') {
    selectedIndex.value = (selectedIndex.value - 1 + Math.max(props.items.length, 1)) % Math.max(props.items.length, 1)
    event.preventDefault()
    return true
  }
  if (event.key === 'Enter') {
    selectItem(selectedIndex.value)
    event.preventDefault()
    return true
  }
  return false
}

defineExpose({ onKeyDown })
</script>

<template>
  <div class="slash-menu">
    <div
      v-for="(item, i) in items"
      :key="item.label"
      class="slash-menu__item"
      :class="{ 'slash-menu__item--active': i === selectedIndex }"
      @click="selectItem(i)"
      @mouseenter="selectedIndex = i"
    >
      <span class="slash-menu__icon">{{ item.icon }}</span>
      <span class="slash-menu__text">
        <span class="slash-menu__label">{{ item.label }}</span>
        <span class="slash-menu__desc">{{ item.description }}</span>
      </span>
    </div>
    <div v-if="items.length === 0" class="slash-menu__empty">无匹配命令</div>
  </div>
</template>

<style scoped>
.slash-menu {
  position: fixed;
  z-index: 200;
  background: var(--color-white);
  border: var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 4px;
  min-width: 220px;
  max-height: 320px;
  overflow-y: auto;
}
.slash-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  user-select: none;
}
.slash-menu__item--active { background: var(--color-gray-100); }
.slash-menu__icon { font-size: 16px; width: 24px; text-align: center; flex-shrink: 0; }
.slash-menu__text { display: flex; flex-direction: column; gap: 1px; }
.slash-menu__label { font-size: 13px; font-weight: 500; color: var(--color-gray-900); }
.slash-menu__desc { font-size: 11px; color: var(--color-gray-500); }
.slash-menu__empty { padding: 8px; font-size: 12px; color: var(--color-gray-400); text-align: center; }
</style>
