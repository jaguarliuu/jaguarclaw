# SSH Node CSV Import Design

## Goal

Allow users to bulk-import SSH nodes from a CSV file. Nodes are created without credentials; users configure passwords/keys individually afterwards. Also remove obsolete DB connector type from backend and frontend.

## Architecture

One-shot import: the frontend uploads raw CSV text via a new `nodes.import` RPC handler. The backend parses all rows, collects all errors (no early exit), deduplicates by both host and alias, and persists valid nodes with an empty credential placeholder. A second handler `nodes.import.template` returns the CSV template for download.

**Tech Stack:** Java / Spring (existing RPC handler pattern), Vue 3 + TypeScript (existing composable/component pattern), manual CSV split (no new library dependency).

---

## CSV Format

Fixed column order, first row is header:

```
alias,displayName,host,port,username,tags,safetyPolicy
```

| Column | Required | Validation |
|---|---|---|
| `alias` | Yes | Non-empty, unique in DB |
| `displayName` | No | Any string |
| `host` | Yes | Non-empty, not localhost/127.0.0.1/::1 |
| `port` | Yes | Integer 1–65535 |
| `username` | Yes | Non-empty, not `root` |
| `tags` | No | Pipe-separated values e.g. `prod\|web`; backend converts to comma-separated for storage |
| `safetyPolicy` | No | `strict` / `standard` / `relaxed`; defaults to `standard` |

`connectorType` is always `ssh` (implicit, not in CSV).
`authType` and `credential` are not in CSV; configured after import.

---

## Backend

### New RPC Handlers

**`nodes.import.template`**
- No parameters
- Returns `{ csv: "<header row + two example rows as string>" }`
- Example rows use clearly fictional IPs (e.g. 192.168.1.10, 192.168.1.11)
- On failure: return `RpcResponse.error` with code `INTERNAL_ERROR`

**`nodes.import`**
- Parameters: `{ csv: "<raw csv string>" }`
- Limit: max 500 data rows; max 512 KB raw string. If exceeded, return a payload-level error `{ row: 0, reason: "..." }` and process nothing. `row: 0` is the sentinel for payload-level errors (not tied to any specific data row). The frontend banner renders `row: 0` errors without a row number prefix.
- Response:
```json
{
  "imported": 3,
  "skipped": 1,
  "skippedAliases": ["web-01"],
  "errors": [
    { "row": 3, "field": "port", "value": "99999", "reason": "Port must be between 1 and 65535" },
    { "row": 4, "field": "host", "reason": "Host is required" }
  ]
}
```

### Parse & Validation Logic

1. If raw CSV is empty, exceeds 512 KB, or exceeds 500 data rows → return payload-level error `{ row: 0, reason: "..." }`, stop. `row: 0` = payload-level; no specific data row.
2. Split by newline, skip blank lines. First non-empty line is the header. Validate header by trimming each column name and comparing case-insensitively to `["alias","displayname","host","port","username","tags","safetypolicy"]`. If mismatch → return `{ row: 0, reason: "Invalid header. Expected: alias,displayName,host,port,username,tags,safetyPolicy" }`, stop.
3. For each data row (row index starts at 2 for error reporting):
   - Parse columns by splitting on `,` (7 expected). If count ≠ 7 → `{ row, reason: "Expected 7 columns, got N" }`, continue to next row.
   - Validate required fields and types; collect all field-level errors for this row.
   - If any errors for this row → add to errors list, skip insert, continue.
4. Deduplicate valid rows (no errors):
   - **Within-CSV host dedup**: if same `host` appeared in an earlier valid row → skip, add alias to `skippedAliases`.
   - **Within-CSV alias dedup**: if same `alias` appeared in an earlier valid row → skip, add alias to `skippedAliases`.
   - **DB host dedup**: if `host` already exists in `node` table → skip, add alias to `skippedAliases`.
   - **DB alias dedup**: if `alias` already exists in `node` table → skip, add alias to `skippedAliases`.
5. Insert remaining valid, non-duplicate rows with:
   - `connectorType = "ssh"`
   - `authType = null`
   - `encryptedCredential = ""`
   - `credentialIv = ""`
   - `tags`: pipe-separated input converted to comma-separated for storage (e.g. `prod|web` → `prod,web`)
   - `safetyPolicy` defaulting to `"standard"` if blank

### Entity Change

`encryptedCredential` and `credentialIv`: keep `NOT NULL` constraint, store `""` (empty string) for imported nodes. An empty `encryptedCredential` is the indicator that this node is not yet configured. No DB migration needed. Downstream code that reads `encryptedCredential` for actual connection (e.g. `NodeService.testConnection`) already guards against unusable credentials via the test result — no special null-check required.

### NodeRepository — new query methods

Add to `NodeRepository` for deduplication checks:
- `existsByHost(String host): boolean` — DB-level host dedup
- `existsByAlias(String alias): boolean` — DB-level alias dedup (check whether this already exists in the repository before adding; if present, no action needed)

### DB Connector Cleanup

- `NodeValidator`: remove `db` branch
- `NodeService`: remove `db` branch in any connector-type conditionals
- `NodeRegisterHandler` / `NodeUpdateHandler`: remove `db`-specific logic if any
- Backend `ConnectorType` constant / switch statements: remove `db`

---

## Frontend

### `types/index.ts`

```ts
export type ConnectorType = 'ssh' | 'k8s'  // remove 'db'
```

Add:
```ts
export interface NodeImportResult {
  imported: number
  skipped: number
  skippedAliases: string[]
  errors: Array<{ row: number; field?: string; value?: string; reason: string }>
}
```

### `useNodeConsole.ts`

Add two methods:

```ts
async importNodes(csvContent: string): Promise<NodeImportResult>
// Calls nodes.import RPC, reloads node list on any imported > 0, returns result.
// On RPC failure, sets error.value and rethrows (consistent with existing pattern).

async downloadTemplate(): Promise<void>
// Calls nodes.import.template RPC, creates a Blob from the csv string,
// triggers browser download as "nodes-template.csv".
// On failure, shows a toast via useNotification (the project's existing notification composable) and does not rethrow
```

### `NodesSection.vue`

**Import toolbar** — add above the node list, alongside the existing "Add Node" button:

- **下载模板** button → calls `downloadTemplate()`
- **导入 CSV** button → triggers hidden `<input type="file" accept=".csv">`, reads file with `FileReader`, calls `importNodes(content)`
- Loading spinner on the import button while in-flight
- After import, show a result banner (not a toast) below the toolbar:
  - Green line: `成功导入 N 个节点` (hidden if imported = 0)
  - Yellow line (if skipped > 0): `跳过 N 个（IP或别名重复）：alias1, alias2`
  - Red lines (if errors): one line per error — `第3行 [port]：Port must be between 1 and 65535`
  - Banner dismisses on next import attempt or when user navigates away
- On `imported > 0`: reload node list

**Node cards** — if `authType === null`, show an orange **"待配置"** badge next to the alias. Clicking the edit button opens the existing edit form with `isEditing = true`, which bypasses the "credential required" guard (that guard only fires for new registrations). `authType` defaults to `'password'` via the existing `|| 'password'` fallback in `openEditForm`. No special scroll or focus behavior required beyond what already exists.

**Banner row rendering:** errors with `row: 0` (payload-level) display without a row number prefix (e.g. `CSV超出500行限制`). Errors with `row >= 2` display as `第N行 [field]：reason`.

**DB connector cleanup:**
- Remove `db` from `connectorType` dropdown options
- Remove all `v-if="connectorType === 'db'"` and `v-else-if="form.connectorType === 'db'"` branches
- Remove DB-specific auth type options (if any separate from ssh/k8s)

---

## Error Handling Summary

| Scenario | Behavior |
|---|---|
| CSV exceeds 500 rows or 512 KB | Single top-level error, nothing processed |
| Header mismatch | Single top-level error, nothing processed |
| Row column count wrong | Per-row error, row skipped |
| Field validation failure | Per-row/field error, row skipped |
| Duplicate host/alias (DB or within-CSV) | Row skipped, alias added to `skippedAliases` |
| Partial success (some rows ok, some errors) | Valid rows imported, errors returned |
| RPC/network failure | `importNodes` sets error.value + rethrows; component shows existing error display |
| Template download failure | Toast via `useToast`, no rethrow |

---

## Testing

**Backend unit tests for `NodeCsvImportHandler`:**
- Valid rows: all imported, correct field mapping, tags pipe→comma conversion
- Missing required field (alias, host, port, username): per-field error reported
- Invalid port (0, 65536, "abc"): error reported
- Localhost host: error reported
- Root username: error reported
- Duplicate host within CSV: second row skipped
- Duplicate alias within CSV: second row skipped
- Duplicate host vs DB: row skipped
- Duplicate alias vs DB: row skipped
- Wrong column count: per-row error
- Wrong header: top-level error, no rows processed
- Over 500 rows: payload-level error (`row: 0`), no rows processed
- CSV exceeds 512 KB: payload-level error (`row: 0`), no rows processed
- Empty CSV: payload-level error (`row: 0`)

**Frontend:** Manual test — download template → fill in real values → upload → verify "待配置" badge → edit node → set authType + credential → save.
