import { createRouter, createWebHistory } from 'vue-router'
import WorkspaceView from '@/views/WorkspaceView.vue'
import SettingsView from '@/views/SettingsView.vue'
import SetupWizard from '@/views/SetupWizard.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: WorkspaceView
    },
    {
      path: '/setup',
      name: 'setup',
      component: SetupWizard
    },
    {
      path: '/settings',
      redirect: '/settings/llm'
    },
    {
      path: '/settings/:section',
      name: 'settings',
      component: SettingsView,
      props: true
    },
    {
      path: '/documents',
      name: 'documents',
      component: WorkspaceView,
    },
    {
      path: '/documents/:id',
      name: 'document-detail',
      component: WorkspaceView,
      props: true,
    },
  ]
})

export default router

