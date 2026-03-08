<script setup lang="ts">
import { computed } from 'vue'
import type { SessionFile } from '@/types'
import AttachmentCard from '@/components/attachments/AttachmentCard.vue'
import { toAttachmentDescriptorFromSessionFile } from '@/utils/attachments'

const props = defineProps<{
  file: SessionFile
  sessionId?: string
}>()

const attachment = computed(() =>
  toAttachmentDescriptorFromSessionFile(props.file, props.sessionId),
)
</script>

<template>
  <AttachmentCard :attachment="attachment" />
</template>
