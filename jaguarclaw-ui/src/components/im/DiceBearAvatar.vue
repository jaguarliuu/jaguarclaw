<template>
  <div class="dicebear-avatar" :style="{ width: size + 'px', height: size + 'px' }" v-html="svgContent" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { createAvatar } from '@dicebear/core'
import * as thumbs from '@dicebear/thumbs'
import * as bottts from '@dicebear/bottts'
import * as funEmoji from '@dicebear/fun-emoji'
import * as lorelei from '@dicebear/lorelei'
import * as micah from '@dicebear/micah'
import * as pixelArt from '@dicebear/pixel-art'
import * as shapes from '@dicebear/shapes'
import * as adventurer from '@dicebear/adventurer'

const STYLE_MAP: Record<string, unknown> = {
  thumbs,
  bottts,
  'fun-emoji': funEmoji,
  lorelei,
  micah,
  'pixel-art': pixelArt,
  shapes,
  adventurer,
}

const props = withDefaults(defineProps<{
  avatarStyle?: string
  avatarSeed?: string
  size?: number
}>(), {
  avatarStyle: 'thumbs',
  avatarSeed: '',
  size: 36,
})

const svgContent = computed(() => {
  const styleKey = props.avatarStyle || 'thumbs'
  const schema = STYLE_MAP[styleKey] ?? thumbs
  const avatar = createAvatar(schema as Parameters<typeof createAvatar>[0], {
    seed: props.avatarSeed || props.avatarStyle || 'default',
    size: props.size,
  })
  return avatar.toString()
})
</script>

<style scoped>
.dicebear-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}
.dicebear-avatar :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
