<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'

interface IndexEntry {
  id: string
  index: number   // 1-based turn number
  preview: string
}

const props = defineProps<{
  entries: IndexEntry[]
  scrollContainer: HTMLElement | null
}>()

const activeId = ref<string | null>(null)

function updateActive() {
  const container = props.scrollContainer
  if (!container || props.entries.length === 0) return

  const containerTop = container.getBoundingClientRect().top
  let closestId: string | null = null
  let closestDist = Infinity

  for (const entry of props.entries) {
    const el = document.getElementById(`msg-${entry.id}`)
    if (!el) continue
    const dist = el.getBoundingClientRect().top - containerTop
    // Track the message closest to (and just below) the top of the container
    if (dist <= 80 && Math.abs(dist) < closestDist) {
      closestDist = Math.abs(dist)
      closestId = entry.id
    }
  }

  // If nothing is above the fold yet, mark the first visible one
  if (!closestId) {
    let minDist = Infinity
    for (const entry of props.entries) {
      const el = document.getElementById(`msg-${entry.id}`)
      if (!el) continue
      const dist = el.getBoundingClientRect().top - containerTop
      if (dist < minDist) {
        minDist = dist
        closestId = entry.id
      }
    }
  }

  activeId.value = closestId
}

function jumpTo(entry: IndexEntry) {
  const el = document.getElementById(`msg-${entry.id}`)
  const container = props.scrollContainer
  if (!el || !container) return
  const target = el.offsetTop - 20
  container.scrollTo({ top: target, behavior: 'smooth' })
}

let scrollListener: (() => void) | null = null

function attachListener() {
  if (scrollListener) props.scrollContainer?.removeEventListener('scroll', scrollListener)
  if (!props.scrollContainer) return
  scrollListener = updateActive
  props.scrollContainer.addEventListener('scroll', scrollListener, { passive: true })
  updateActive()
}

onMounted(attachListener)

watch(() => props.scrollContainer, attachListener)
watch(() => props.entries.length, () => {
  // Re-run after DOM updates when new messages arrive
  setTimeout(updateActive, 50)
})

onUnmounted(() => {
  if (scrollListener && props.scrollContainer) {
    props.scrollContainer.removeEventListener('scroll', scrollListener)
  }
})
</script>

<template>
  <nav class="conv-index">
    <div class="index-header">TURNS</div>
    <ul class="index-list">
      <li
        v-for="entry in entries"
        :key="entry.id"
        class="index-item"
        :class="{ active: activeId === entry.id }"
        @click="jumpTo(entry)"
        :title="entry.preview"
      >
        <span class="item-num">{{ entry.index }}</span>
        <span class="item-text">{{ entry.preview }}</span>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.conv-index {
  width: 152px;
  border-left: 1.5px solid var(--color-gray-100);
  padding: 0 0 24px 16px;
}

.index-header {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--color-gray-300);
  text-transform: uppercase;
  padding: 2px 6px 10px;
}

.index-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.index-item {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 5px 6px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast);
}

.index-item:hover {
  background: var(--color-gray-50);
}

.index-item.active {
  background: var(--color-gray-50);
}

.index-item.active .item-num {
  color: var(--color-primary);
  font-weight: 600;
}

.index-item.active .item-text {
  color: var(--color-gray-700);
}

.item-num {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-gray-300);
  min-width: 14px;
  flex-shrink: 0;
  margin-top: 1px;
  line-height: 1.5;
  transition: color var(--duration-fast);
}

.item-text {
  font-size: 11px;
  color: var(--color-gray-400);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.45;
  transition: color var(--duration-fast);
}
</style>
