<script setup lang="ts">
import { computed } from 'vue'
import type { AttachedContext } from '@/types'
import AttachmentCard from '@/components/attachments/AttachmentCard.vue'
import { toAttachmentDescriptorFromContext } from '@/utils/attachments'

const props = defineProps<{
  context: AttachedContext
  sessionId?: string | null
}>()

const attachment = computed(() => toAttachmentDescriptorFromContext(props.context, props.sessionId))
</script>

<template>
  <AttachmentCard v-if="attachment" :attachment="attachment" />
</template>
