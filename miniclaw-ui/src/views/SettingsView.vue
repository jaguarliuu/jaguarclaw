<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useWebSocket } from '@/composables/useWebSocket'
import SettingsSidebar from '@/components/settings/SettingsSidebar.vue'
import LlmSection from '@/components/settings/LlmSection.vue'
import ToolsConfigSection from '@/components/settings/ToolsConfigSection.vue'
import SkillsSection from '@/components/settings/SkillsSection.vue'
import MemorySection from '@/components/settings/MemorySection.vue'
import McpSection from '@/components/settings/McpSection.vue'
import SoulSection from '@/components/settings/SoulSection.vue'
import SystemSection from '@/components/settings/SystemSection.vue'
import NodesSection from '@/components/settings/NodesSection.vue'
import DataSourcesSection from '@/components/settings/DataSourcesSection.vue'
import ChannelsSection from '@/components/settings/ChannelsSection.vue'
import AuditLogSection from '@/components/settings/AuditLogSection.vue'
import SchedulesSection from '@/components/settings/SchedulesSection.vue'
import PlaceholderSection from '@/components/settings/PlaceholderSection.vue'
import ConnectionStatus from '@/components/ConnectionStatus.vue'

const route = useRoute()
const { state: connectionState } = useWebSocket()

const currentSection = computed(() => {
  return route.params.section as string || 'llm'
})
</script>

<template>
  <div class="settings-view">
    <!-- Main content -->
    <div class="settings-body">
      <SettingsSidebar />

      <main class="settings-content">
        <LlmSection v-if="currentSection === 'llm'" />
        <ToolsConfigSection v-else-if="currentSection === 'tools'" />
        <SkillsSection v-else-if="currentSection === 'skills'" />
        <MemorySection v-else-if="currentSection === 'memory'" />
        <McpSection v-else-if="currentSection === 'mcp'" />
        <SoulSection v-else-if="currentSection === 'soul'" />
        <SystemSection v-else-if="currentSection === 'system'" />
        <NodesSection v-else-if="currentSection === 'nodes'" />
        <DataSourcesSection v-else-if="currentSection === 'datasources'" />
        <ChannelsSection v-else-if="currentSection === 'channels'" />
        <AuditLogSection v-else-if="currentSection === 'audit'" />
        <SchedulesSection v-else-if="currentSection === 'tasks'" />
        <PlaceholderSection v-else title="Not Found" message="Section not found" />
      </main>
    </div>

    <!-- Footer -->
    <footer class="settings-footer">
      <ConnectionStatus :state="connectionState" />
    </footer>
  </div>
</template>

<style scoped>
.settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-white);
}

.settings-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.settings-content {
  flex: 1;
  overflow: hidden;
}

.settings-footer {
  padding: 12px 20px;
  border-top: var(--border-light);
}
</style>
