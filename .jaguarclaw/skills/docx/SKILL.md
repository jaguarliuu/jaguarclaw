---
name: docx
description: "Use this skill whenever the user wants to create, read, edit, or manipulate Word documents (.docx files). Triggers include: any mention of \"Word doc\", \"word document\", \".docx\", or requests to produce professional documents with formatting like tables of contents, headings, page numbers, or letterheads. Also use when extracting or reorganizing content from .docx files, inserting or replacing images in documents, or converting content into a polished Word document. If the user asks for a \"report\", \"memo\", \"letter\", \"template\", or similar deliverable as a Word or .docx file, use this skill. Do NOT use for PDFs, spreadsheets, Google Docs, or general coding tasks unrelated to document generation."
license: Proprietary. LICENSE.txt has complete terms
allowed-tools: [create_docx, read_docx, read_file, write_file]
---

# DOCX creation, editing, and analysis

## Creating New Documents

Use the `create_docx` tool with a declarative content array.

### Content types

| type | Required props | Optional props |
|------|---------------|----------------|
| `heading` | `level` (1–6), `text` | — |
| `paragraph` | `text` | `align`: left/center/right/both |
| `table` | `rows` (string[][]) | `header` (string[]) |
| `list` | `items` (string[]) | `ordered`: boolean |
| `image` | `path`, `width` (px), `height` (px) | — |
| `page_break` | — | — |

### page_setup (optional)

```json
{
  "orientation": "portrait",
  "header": "Company Name — Confidential",
  "footer": "Page {n}"
}
```
Use `{n}` in footer for page number field.

### Example

```json
{
  "path": "/workspace/report.docx",
  "page_setup": {"orientation":"portrait","header":"ACME Corp","footer":"Page {n}"},
  "content": [
    {"type":"heading","level":1,"text":"Annual Report 2025"},
    {"type":"paragraph","text":"This report covers...","align":"both"},
    {"type":"table","header":["Division","Revenue","Growth"],"rows":[["North","$450M","12%"]]},
    {"type":"page_break"},
    {"type":"heading","level":2,"text":"Appendix"}
  ]
}
```

## Reading Existing Documents

```json
{"path": "/workspace/doc.docx"}
```
Returns `{"content": [{type, text/rows, ...}, ...]}`.

## Editing Existing Documents

1. `read_docx` to extract current content
2. Modify the content array
3. `create_docx` with updated content (same path to overwrite)

## Critical Rules

- **Page defaults to A4** portrait. Use `page_setup.orientation` to change.
- **Tables**: all rows must have same column count as header.
- **Images**: file must exist at specified path before calling `create_docx`.
- **PDF export is not supported** — deliver .docx files only.
- **Tracked changes and comments** are not supported — create clean documents only.
