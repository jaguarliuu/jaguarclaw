import { Extension } from '@tiptap/core'
import { Suggestion } from '@tiptap/suggestion'
import { createApp, type App } from 'vue'
import DocumentSlashMenu, { type SlashMenuItem } from './DocumentSlashMenu.vue'
import { CHART_PRESETS } from './chartPresets'

export type SlashAiActionCallback = (action: string) => void

function getItems(editor: any, query: string, onAiAction: SlashAiActionCallback): SlashMenuItem[] {
  const contentItems: SlashMenuItem[] = [
    {
      label: '标题 1',
      description: '大标题',
      icon: 'H1',
      action: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
    },
    {
      label: '标题 2',
      description: '中标题',
      icon: 'H2',
      action: () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
    },
    {
      label: '标题 3',
      description: '小标题',
      icon: 'H3',
      action: () => editor.chain().focus().toggleHeading({ level: 3 }).run(),
    },
    {
      label: '无序列表',
      description: '圆点列表',
      icon: '•',
      action: () => editor.chain().focus().toggleBulletList().run(),
    },
    {
      label: '有序列表',
      description: '编号列表',
      icon: '①',
      action: () => editor.chain().focus().toggleOrderedList().run(),
    },
    {
      label: '代码块',
      description: '代码片段',
      icon: '〈〉',
      action: () => editor.chain().focus().toggleCodeBlock().run(),
    },
    {
      label: '引用块',
      description: '引用文字',
      icon: '"',
      action: () => editor.chain().focus().toggleBlockquote().run(),
    },
    {
      label: '分隔线',
      description: '水平分隔',
      icon: '—',
      action: () => editor.chain().focus().setHorizontalRule().run(),
    },
    {
      label: 'Mermaid 图',
      description: '流程/时序图',
      icon: '⬡',
      action: () =>
        editor
          .chain()
          .focus()
          .insertContent({
            type: 'codeBlock',
            attrs: { language: 'mermaid' },
            content: [{ type: 'text', text: 'graph TD\n  A --> B' }],
          })
          .run(),
    },
    ...Object.entries(CHART_PRESETS).map(([key, preset]) => ({
      label: preset.label,
      description: `插入${preset.label}`,
      icon: '◈',
      action: () =>
        editor
          .chain()
          .focus()
          .insertContent({ type: 'chartBlock', attrs: { spec: JSON.stringify(preset.spec) } })
          .run(),
    })),
  ]

  const aiItems: SlashMenuItem[] = [
    {
      label: 'AI 续写',
      description: '从光标处续写内容',
      icon: '✍',
      action: () => onAiAction('continue'),
    },
    {
      label: 'AI 润色',
      description: '改善全文表达',
      icon: '✨',
      action: () => onAiAction('optimize'),
    },
    {
      label: 'AI 总结',
      description: '提炼核心要点',
      icon: '📝',
      action: () => onAiAction('summarize'),
    },
    {
      label: 'AI 翻译',
      description: '中英互译',
      icon: '🌐',
      action: () => onAiAction('translate'),
    },
  ]

  const all = [...contentItems, ...aiItems]
  if (!query) return all
  const q = query.toLowerCase()
  return all.filter(
    (item) => item.label.toLowerCase().includes(q) || item.description.toLowerCase().includes(q),
  )
}

export function createSlashExtension(onAiAction: SlashAiActionCallback) {
  return Extension.create({
    name: 'slashCommand',

    addProseMirrorPlugins() {
      return [
        Suggestion({
          editor: this.editor,
          char: '/',
          allowSpaces: false,
          startOfLine: false,

          items: ({ query }: { query: string }) => {
            return getItems(this.editor, query, onAiAction)
          },

          // Delete the slash+query range, then run the selected item's action.
          // Actions in getItems do NOT call deleteRange — deletion happens here only.
          command: ({ editor, range, props }: any) => {
            editor.chain().focus().deleteRange(range).run()
            props.action()
          },

          render: () => {
            let mountEl: HTMLDivElement | null = null
            let vueApp: App | null = null
            let exposedOnKeyDown: ((e: KeyboardEvent) => boolean) | null = null

            function showMenu(items: SlashMenuItem[], clientRect: () => DOMRect | null) {
              if (!mountEl) {
                mountEl = document.createElement('div')
                document.body.appendChild(mountEl)
              }
              if (vueApp) { vueApp.unmount(); vueApp = null }
              exposedOnKeyDown = null

              vueApp = createApp(DocumentSlashMenu, { items })
              const instance = vueApp.mount(mountEl) as any
              // After mount, grab the exposed onKeyDown from the component instance
              exposedOnKeyDown = typeof instance.onKeyDown === 'function' ? instance.onKeyDown : null

              // Position the inner element (the .slash-menu div rendered by the component)
              const rect = clientRect()
              const el = mountEl.firstElementChild as HTMLElement | null
              if (el && rect) {
                el.style.position = 'fixed'
                el.style.top = `${rect.bottom + 4}px`
                el.style.left = `${rect.left}px`
              }
            }

            function destroyMenu() {
              if (vueApp) { vueApp.unmount(); vueApp = null }
              if (mountEl) { mountEl.remove(); mountEl = null }
              exposedOnKeyDown = null
            }

            return {
              onStart(props: any) {
                if (props.clientRect) showMenu(props.items, props.clientRect)
              },

              onUpdate(props: any) {
                if (props.clientRect) showMenu(props.items, props.clientRect)
              },

              onKeyDown(props: { event: KeyboardEvent }): boolean {
                if (props.event.key === 'Escape') {
                  destroyMenu()
                  return true
                }
                if (exposedOnKeyDown) return exposedOnKeyDown(props.event)
                return false
              },

              onExit() {
                destroyMenu()
              },
            }
          },
        }),
      ]
    },
  })
}
