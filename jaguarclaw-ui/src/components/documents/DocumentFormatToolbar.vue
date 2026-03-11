<!-- jaguarclaw-ui/src/components/documents/DocumentFormatToolbar.vue -->
<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{ editor: Editor | undefined }>()

function insertImage() {
  const url = window.prompt('输入图片 URL：')
  if (url && props.editor) {
    props.editor.chain().focus().setImage({ src: url }).run()
  }
}

function toggleLink() {
  if (!props.editor) return
  if (props.editor.isActive('link')) {
    props.editor.chain().focus().unsetLink().run()
    return
  }
  const url = window.prompt('输入链接 URL：')
  if (url) {
    props.editor.chain().focus().setLink({ href: url }).run()
  }
}
</script>

<template>
  <div v-if="editor" class="format-toolbar">
    <button
      :class="{ active: editor.isActive('bold') }"
      title="Bold (Ctrl+B)"
      @click="editor?.chain().focus().toggleBold().run()"
    >B</button>
    <button
      :class="{ active: editor.isActive('italic') }"
      title="Italic (Ctrl+I)"
      @click="editor?.chain().focus().toggleItalic().run()"
    ><em>I</em></button>
    <button
      :class="{ active: editor.isActive('strike') }"
      title="Strikethrough"
      @click="editor?.chain().focus().toggleStrike().run()"
    ><s>S</s></button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('heading', { level: 1 }) }"
      title="Heading 1"
      @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
    >H1</button>
    <button
      :class="{ active: editor.isActive('heading', { level: 2 }) }"
      title="Heading 2"
      @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()"
    >H2</button>
    <button
      :class="{ active: editor.isActive('heading', { level: 3 }) }"
      title="Heading 3"
      @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()"
    >H3</button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('bulletList') }"
      title="Bullet list"
      @click="editor?.chain().focus().toggleBulletList().run()"
    >• List</button>
    <button
      :class="{ active: editor.isActive('orderedList') }"
      title="Numbered list"
      @click="editor?.chain().focus().toggleOrderedList().run()"
    >1. List</button>

    <div class="format-toolbar__sep" />

    <button
      :class="{ active: editor.isActive('codeBlock') }"
      title="Code block"
      @click="editor?.chain().focus().toggleCodeBlock().run()"
    >&lt;/&gt;</button>
    <button
      :class="{ active: editor.isActive('blockquote') }"
      title="Blockquote"
      @click="editor?.chain().focus().toggleBlockquote().run()"
    >"</button>
    <button
      title="Horizontal rule"
      @click="editor?.chain().focus().setHorizontalRule().run()"
    >—</button>
    <button
      title="Insert Mermaid diagram"
      @click="editor?.chain().focus().insertContent({ type: 'codeBlock', attrs: { language: 'mermaid' }, content: [{ type: 'text', text: 'graph TD\n  A --> B' }] }).run()"
    >⬡ Mermaid</button>

    <div class="format-toolbar__sep" />

    <button title="Insert Image" @click="insertImage">🖼</button>
    <button :class="{ active: editor.isActive('link') }" title="Link" @click="toggleLink">🔗</button>

    <div class="format-toolbar__sep" />

    <button title="Insert Table" @click="editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()">⊞ Table</button>
  </div>
</template>

<style scoped>
.format-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 20px;
  border-bottom: var(--border);
  background: var(--color-white);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.format-toolbar button {
  padding: 3px 8px;
  font-size: 12px;
  font-family: var(--font-ui);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-gray-700);
  cursor: pointer;
  line-height: 1.4;
  min-width: 28px;
  transition: background var(--duration-fast) var(--ease-out);
}
.format-toolbar button:hover {
  background: var(--color-gray-100);
  border-color: var(--color-gray-200);
}
.format-toolbar button.active {
  background: var(--color-gray-200);
  color: var(--color-gray-900);
  font-weight: 600;
}
.format-toolbar__sep {
  width: 1px;
  height: 16px;
  background: var(--color-gray-200);
  margin: 0 4px;
}
</style>
