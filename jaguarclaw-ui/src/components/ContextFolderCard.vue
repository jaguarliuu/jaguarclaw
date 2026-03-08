<script setup lang="ts">
import { computed } from 'vue'
import type { AttachedContext } from '@/types'
import AttachmentCard from '@/components/attachments/AttachmentCard.vue'
import { toFolderAttachmentDescriptor } from '@/utils/attachments'

const props = defineProps<{
  context: AttachedContext
}>()

const attachment = computed(() => toFolderAttachmentDescriptor(props.context))
</script>

<template>
  <AttachmentCard v-if="attachment" :attachment="attachment" />
</template>
