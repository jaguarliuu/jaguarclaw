# Office Java Tools Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan.

**Goal:** Replace Python/Node.js-based xlsx and docx skills with pure Java tools using Apache POI, eliminating all external runtime dependencies for internal deployments.

**Architecture:** Create 4 Spring `@Component` Tool beans (`create_xlsx`, `read_xlsx`, `create_docx`, `read_docx`) in `src/main/java/com/jaguarliu/ai/tools/builtin/office/`. Each tool accepts declarative JSON from the LLM and uses Apache POI 5.2.5 (already in pom.xml) to produce/read the file. Update the two SKILL.md files to call these Java tools instead of bash+python/node. Delete the now-unused `scripts/` directories.

**Tech Stack:** Apache POI 5.2.5 (poi-ooxml, already present), Apache Tika 2.9.2 (already present), JUnit 5 + `@TempDir`, Spring `@Component`, `Mono<ToolResult>`

---

## File Map

### Create
| File | Responsibility |
|------|---------------|
| `src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateTool.java` | create_xlsx — build full workbook from JSON spec via Apache POI |
| `src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadTool.java` | read_xlsx — read cell data from existing xlsx |
| `src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateTool.java` | create_docx — build Word doc from JSON content array |
| `src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxReadTool.java` | read_docx — extract text/structure from existing docx |
| `src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateToolTest.java` | Unit tests |
| `src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadToolTest.java` | Unit tests |
| `src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateToolTest.java` | Unit tests |
| `src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxReadToolTest.java` | Unit tests |

### Modify
| File | Change |
|------|--------|
| `.jaguarclaw/skills/xlsx/SKILL.md` | Remove Python/LibreOffice instructions; update allowed-tools; rewrite workflow |
| `.jaguarclaw/skills/docx/SKILL.md` | Remove Node.js/Python instructions; update allowed-tools; rewrite workflow |

### Delete
- `.jaguarclaw/skills/xlsx/scripts/`
- `.jaguarclaw/skills/docx/scripts/`

---

## Key Implementation Notes

### Tool Bean Pattern
All tools follow the existing pattern in `src/main/java/com/jaguarliu/ai/tools/builtin/`:
- `@Component` Spring bean
- Implements `Tool` interface: `getDefinition()` + `execute(Map<String,Object>)`
- `execute()` returns `Mono<ToolResult>` via `Mono.fromCallable()`
- Auto-discovered by `ToolRegistry` — no manual registration needed

### create_xlsx JSON Schema
The LLM calls the tool with this structure:
```json
{
  "path": "/workspace/report.xlsx",
  "sheets": [{
    "name": "Sheet1",
    "freeze_row": 1,
    "column_widths": [30, 15, 15],
    "rows": [
      [
        {"value": "Header", "bold": true, "bg_color": "4472C4", "fg_color": "FFFFFF"},
        {"value": "Amount", "bold": true, "bg_color": "4472C4"}
      ],
      [
        {"value": "Revenue"},
        {"value": 500000, "format": "$#,##0"}
      ],
      [
        {"value": "Total"},
        {"formula": "SUM(B2:B2)", "format": "$#,##0", "bold": true}
      ]
    ]
  }]
}
```
Cell properties: `value`, `formula`, `format`, `bold`, `italic`, `bg_color`, `fg_color`, `align`, `border`, `colspan`, `rowspan`

### create_docx JSON Schema
```json
{
  "path": "/workspace/report.docx",
  "page_setup": {"orientation": "portrait", "header": "Company", "footer": "Page {n}"},
  "content": [
    {"type": "heading",   "level": 1, "text": "Title"},
    {"type": "paragraph", "text": "Body text", "align": "both"},
    {"type": "table",     "header": ["Col1","Col2"], "rows": [["A","B"]]},
    {"type": "list",      "items": ["Item 1", "Item 2"], "ordered": false},
    {"type": "image",     "path": "/workspace/logo.png", "width": 400, "height": 200},
    {"type": "page_break"}
  ]
}
```

### Apache POI Key APIs
- `XSSFWorkbook` / `XSSFSheet` / `XSSFRow` / `XSSFCell` — xlsx
- `XSSFCellStyle` + `XSSFFont` — formatting
- `XSSFFormulaEvaluator.evaluateAll()` — pre-compute formula cached values
- `XSSFColor(byte[3], null)` — hex color parsing
- `sheet.setColumnWidth(i, chars * 256)` — column widths
- `sheet.createFreezePane(cols, rows)` — freeze pane
- `sheet.addMergedRegion(CellRangeAddress)` — cell merge
- `XWPFDocument` / `XWPFParagraph` / `XWPFTable` / `XWPFTableRow` — docx
- `para.setStyle("Heading1")` — heading styles
- `doc.createHeader(HeaderFooterType.DEFAULT)` — headers
- `doc.createFooter(HeaderFooterType.DEFAULT)` — footers
- PAGE field: CTFldChar with STFldCharType.BEGIN/END + CTText instrText " PAGE "
- `run.addPicture(InputStream, PICTURE_TYPE_PNG, filename, Units.toEMU(w), Units.toEMU(h))`

---

## Chunk 1: xlsx Tools

### Task 1: XlsxCreateTool tests + implementation

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateToolTest.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateTool.java`

- [ ] **Step 1: Create test file**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class XlsxCreateToolTest {
    private final XlsxCreateTool tool = new XlsxCreateTool();

    @Test
    void missingPath_returnsError() {
        ToolResult r = tool.execute(Map.of("sheets", List.of())).block();
        assertFalse(r.isSuccess());
    }

    @Test
    void createsFileWithStringAndNumberCells(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("test.xlsx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "sheets", List.of(Map.of(
                "name", "Data",
                "rows", List.of(
                    List.of(Map.of("value","Name"), Map.of("value","Score")),
                    List.of(Map.of("value","Alice"), Map.of("value", 95.5))
                )
            ))
        )).block();
        assertTrue(r.isSuccess(), r.getContent());
        try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path))) {
            assertEquals("Name", wb.getSheet("Data").getRow(0).getCell(0).getStringCellValue());
            assertEquals(95.5, wb.getSheet("Data").getRow(1).getCell(1).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void setsFormulasAndEvaluates(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("f.xlsx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "sheets", List.of(Map.of(
                "name", "S",
                "rows", List.of(
                    List.of(Map.of("value", 10.0)),
                    List.of(Map.of("value", 20.0)),
                    List.of(Map.of("formula","SUM(A1:A2)"))
                )
            ))
        )).block();
        assertTrue(r.isSuccess());
        try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path))) {
            XSSFFormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            assertEquals(30.0, ev.evaluate(wb.getSheetAt(0).getRow(2).getCell(0)).getNumberValue(), 0.001);
        }
    }

    @Test
    void appliesBoldAndBgColor(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("s.xlsx").toString();
        tool.execute(Map.of(
            "path", path,
            "sheets", List.of(Map.of(
                "name", "S",
                "rows", List.of(List.of(Map.of("value","H","bold",true,"bg_color","4472C4")))
            ))
        )).block();
        try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path))) {
            assertTrue(wb.getSheetAt(0).getRow(0).getCell(0).getCellStyle().getFont().getBold());
        }
    }

    @Test
    void freezeRowAndColumnWidths(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("fr.xlsx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "sheets", List.of(Map.of(
                "name","S","freeze_row",1,"column_widths",List.of(30.0),
                "rows", List.of(List.of(Map.of("value","A")))
            ))
        )).block();
        assertTrue(r.isSuccess());
        try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path))) {
            assertTrue(wb.getSheetAt(0).getColumnWidth(0) > 0);
            assertNotNull(wb.getSheetAt(0).getPaneInformation());
        }
    }
}
```

- [ ] **Step 2: Run — confirm compile error (class not found)**
```bash
mvn test -pl . -Dtest=XlsxCreateToolTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create XlsxCreateTool.java**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class XlsxCreateTool implements Tool {

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("create_xlsx")
            .description("Create an Excel (.xlsx) file from a declarative JSON spec. Supports multiple sheets, string/number/formula cells, formatting (bold, italic, bg_color, fg_color, format, align, border), column widths, freeze panes, merged cells. Formulas are pre-evaluated.")
            .parameters(Map.of(
                "type", "object",
                "properties", Map.of(
                    "path",   Map.of("type","string","description","Output .xlsx file path"),
                    "sheets", Map.of("type","array","description",
                        "Array of sheet defs: [{name, freeze_row?, freeze_col?, column_widths?, rows}]. " +
                        "rows is 2D array of cell defs: {value?, formula?, format?, bold?, italic?, bg_color?, fg_color?, align?, border?, colspan?, rowspan?}")
                ),
                "required", List.of("path","sheets")
            ))
            .hitl(false)
            .tags(List.of("office","xlsx"))
            .riskLevel("low")
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) return ToolResult.error("path is required");

            List<Map<String, Object>> sheets = (List<Map<String, Object>>) arguments.get("sheets");
            if (sheets == null || sheets.isEmpty()) return ToolResult.error("sheets is required and must not be empty");

            var parent = Paths.get(path).getParent();
            if (parent != null) Files.createDirectories(parent);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                for (var sheetDef : sheets) buildSheet(workbook, sheetDef);

                try {
                    workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
                } catch (Exception e) {
                    log.warn("Formula evaluation partial failure (non-fatal): {}", e.getMessage());
                }

                try (var fos = new FileOutputStream(path)) {
                    workbook.write(fos);
                }
            }
            return ToolResult.success("Created: " + path);
        });
    }

    @SuppressWarnings("unchecked")
    private void buildSheet(XSSFWorkbook wb, Map<String, Object> def) {
        XSSFSheet sheet = wb.createSheet((String) def.getOrDefault("name", "Sheet"));

        List<Number> colWidths = (List<Number>) def.get("column_widths");
        if (colWidths != null)
            for (int i = 0; i < colWidths.size(); i++)
                sheet.setColumnWidth(i, (int)(colWidths.get(i).doubleValue() * 256));

        int freezeRow = toInt(def.get("freeze_row"), 0);
        int freezeCol = toInt(def.get("freeze_col"), 0);
        if (freezeRow > 0 || freezeCol > 0) sheet.createFreezePane(freezeCol, freezeRow);

        List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>) def.get("rows");
        if (rows == null) return;

        for (int ri = 0; ri < rows.size(); ri++) {
            var cells = rows.get(ri);
            if (cells == null) continue;
            XSSFRow row = sheet.createRow(ri);
            for (int ci = 0; ci < cells.size(); ci++) {
                var cellDef = cells.get(ci);
                if (cellDef == null) continue;
                XSSFCell cell = row.createCell(ci);
                applyValue(cell, cellDef);
                cell.setCellStyle(buildStyle(wb, cellDef));
                int cs = toInt(cellDef.get("colspan"), 1);
                int rs = toInt(cellDef.get("rowspan"), 1);
                if (cs > 1 || rs > 1)
                    sheet.addMergedRegion(new CellRangeAddress(ri, ri+rs-1, ci, ci+cs-1));
            }
        }
    }

    private void applyValue(XSSFCell cell, Map<String, Object> def) {
        String formula = (String) def.get("formula");
        Object value   = def.get("value");
        if (formula != null)              cell.setCellFormula(formula);
        else if (value instanceof Number) cell.setCellValue(((Number)value).doubleValue());
        else if (value instanceof Boolean)cell.setCellValue((Boolean)value);
        else if (value != null)           cell.setCellValue(value.toString());
    }

    private XSSFCellStyle buildStyle(XSSFWorkbook wb, Map<String, Object> def) {
        XSSFCellStyle style = wb.createCellStyle();
        boolean bold   = Boolean.TRUE.equals(def.get("bold"));
        boolean italic = Boolean.TRUE.equals(def.get("italic"));
        String fgColor = (String) def.get("fg_color");
        if (bold || italic || fgColor != null) {
            XSSFFont font = wb.createFont();
            font.setBold(bold);
            font.setItalic(italic);
            if (fgColor != null) font.setColor(hexColor(fgColor));
            style.setFont(font);
        }
        String bgColor = (String) def.get("bg_color");
        if (bgColor != null) {
            style.setFillForegroundColor(hexColor(bgColor));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        String format = (String) def.get("format");
        if (format != null) style.setDataFormat(wb.createDataFormat().getFormat(format));
        String align = (String) def.get("align");
        if (align != null) style.setAlignment(switch(align.toLowerCase()) {
            case "center" -> HorizontalAlignment.CENTER;
            case "right"  -> HorizontalAlignment.RIGHT;
            default       -> HorizontalAlignment.LEFT;
        });
        if (Boolean.TRUE.equals(def.get("border"))) {
            style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        }
        return style;
    }

    private XSSFColor hexColor(String hex) {
        hex = hex.replaceAll("^#","");
        return new XSSFColor(new byte[]{
            (byte)Integer.parseInt(hex.substring(0,2),16),
            (byte)Integer.parseInt(hex.substring(2,4),16),
            (byte)Integer.parseInt(hex.substring(4,6),16)
        }, null);
    }

    private int toInt(Object v, int def) { return v instanceof Number n ? n.intValue() : def; }
}
```

- [ ] **Step 4: Run tests — confirm pass**
```bash
mvn test -pl . -Dtest=XlsxCreateToolTest -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateTool.java \
        src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxCreateToolTest.java
git commit -m "feat(office): add create_xlsx Java tool via Apache POI"
```

---

### Task 2: XlsxReadTool tests + implementation

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadToolTest.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadTool.java`

- [ ] **Step 1: Create test file**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class XlsxReadToolTest {
    private final XlsxReadTool tool = new XlsxReadTool();

    private Path makeFile(Path dir) throws Exception {
        Path p = dir.resolve("t.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet s = wb.createSheet("Sheet1");
            XSSFRow r0 = s.createRow(0); r0.createCell(0).setCellValue("Name"); r0.createCell(1).setCellValue("Score");
            XSSFRow r1 = s.createRow(1); r1.createCell(0).setCellValue("Alice"); r1.createCell(1).setCellValue(95.5);
            try (var out = new FileOutputStream(p.toFile())) { wb.write(out); }
        }
        return p;
    }

    @Test void missingPath_error() { assertFalse(tool.execute(Map.of()).block().isSuccess()); }

    @Test void readsSheetNames(@TempDir Path tmp) throws Exception {
        assertTrue(tool.execute(Map.of("path", makeFile(tmp).toString())).block().getContent().contains("Sheet1"));
    }

    @Test void readsCells(@TempDir Path tmp) throws Exception {
        ToolResult r = tool.execute(Map.of("path", makeFile(tmp).toString(), "sheet", "Sheet1")).block();
        assertTrue(r.getContent().contains("Alice"));
    }
}
```

- [ ] **Step 2: Confirm failure**
```bash
mvn test -pl . -Dtest=XlsxReadToolTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create XlsxReadTool.java**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.util.*;

@Slf4j
@Component
public class XlsxReadTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("read_xlsx")
            .description("Read an Excel (.xlsx) file. Returns sheet names and cell values as JSON. Optionally restrict to one sheet.")
            .parameters(Map.of(
                "type","object",
                "properties", Map.of(
                    "path",  Map.of("type","string","description","Path to .xlsx file"),
                    "sheet", Map.of("type","string","description","Sheet name to read (all sheets if omitted)")
                ),
                "required", List.of("path")
            ))
            .hitl(false).tags(List.of("office","xlsx")).riskLevel("low").build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) return ToolResult.error("path is required");
            String targetSheet = (String) arguments.get("sheet");

            try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(path))) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));

                FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
                Map<String, Object> data = new LinkedHashMap<>();
                for (String name : names) {
                    if (targetSheet != null && !targetSheet.equals(name)) continue;
                    Sheet sheet = wb.getSheet(name);
                    List<List<String>> rows = new ArrayList<>();
                    for (Row row : sheet) {
                        List<String> cells = new ArrayList<>();
                        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            cells.add(cell == null ? "" : cellString(cell, eval));
                        }
                        rows.add(cells);
                    }
                    data.put(name, rows);
                }
                return ToolResult.success(MAPPER.writeValueAsString(Map.of("sheets", names, "data", data)));
            }
        });
    }

    private String cellString(Cell cell, FormulaEvaluator eval) {
        try {
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return "";
            return switch (cv.getCellType()) {
                case NUMERIC -> { double d = cv.getNumberValue(); yield d == Math.floor(d) ? String.valueOf((long)d) : String.valueOf(d); }
                case STRING  -> cv.getStringValue();
                case BOOLEAN -> String.valueOf(cv.getBooleanValue());
                default      -> "";
            };
        } catch (Exception e) { return cell.toString(); }
    }
}
```

- [ ] **Step 4: Run tests — confirm pass**
```bash
mvn test -pl . -Dtest=XlsxReadToolTest -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadTool.java \
        src/test/java/com/jaguarliu/ai/tools/builtin/office/XlsxReadToolTest.java
git commit -m "feat(office): add read_xlsx Java tool"
```

---

## Chunk 2: docx Tools

### Task 3: DocxCreateTool tests + implementation

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateToolTest.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateTool.java`

- [ ] **Step 1: Create test file**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DocxCreateToolTest {
    private final DocxCreateTool tool = new DocxCreateTool();

    @Test void missingPath_error() {
        assertFalse(tool.execute(Map.of("content", List.of())).block().isSuccess());
    }

    @Test void headingAndParagraph(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("t.docx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "content", List.of(
                Map.of("type","heading","level",1,"text","Title"),
                Map.of("type","paragraph","text","Body text.")
            )
        )).block();
        assertTrue(r.isSuccess(), r.getContent());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            assertTrue(doc.getParagraphs().size() >= 2);
            assertEquals("Title", doc.getParagraphs().get(0).getText());
        }
    }

    @Test void tableWithHeaderAndRows(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("t.docx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "content", List.of(Map.of(
                "type","table",
                "header", List.of("Name","Score"),
                "rows", List.of(List.of("Alice","95"), List.of("Bob","87"))
            ))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            assertEquals(1, doc.getTables().size());
            XWPFTable t = doc.getTables().get(0);
            assertEquals(3, t.getNumberOfRows());
            assertEquals("Name",  t.getRow(0).getCell(0).getText());
            assertEquals("Alice", t.getRow(1).getCell(0).getText());
        }
    }

    @Test void listItems(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("t.docx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "content", List.of(Map.of("type","list","items",List.of("A","B"),"ordered",false))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            assertTrue(doc.getParagraphs().size() >= 2);
        }
    }

    @Test void headerAndFooter(@TempDir Path tmp) throws Exception {
        String path = tmp.resolve("t.docx").toString();
        ToolResult r = tool.execute(Map.of(
            "path", path,
            "page_setup", Map.of("header","Company","footer","Page {n}"),
            "content", List.of(Map.of("type","paragraph","text","Body"))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
            assertFalse(doc.getHeaderList().isEmpty());
            assertTrue(doc.getHeaderList().get(0).getText().contains("Company"));
        }
    }
}
```

- [ ] **Step 2: Confirm failure**
```bash
mvn test -pl . -Dtest=DocxCreateToolTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create DocxCreateTool.java**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DocxCreateTool implements Tool {

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("create_docx")
            .description("Create a Word (.docx) document from a declarative JSON spec. " +
                "Content types: heading{level,text}, paragraph{text,align?}, table{header?,rows}, " +
                "list{items,ordered?}, image{path,width,height}, page_break. " +
                "Optional page_setup: {orientation?,header?,footer?} (use {n} in footer for page number).")
            .parameters(Map.of(
                "type","object",
                "properties", Map.of(
                    "path",       Map.of("type","string","description","Output .docx file path"),
                    "content",    Map.of("type","array","description","Array of content items (heading/paragraph/table/list/image/page_break)"),
                    "page_setup", Map.of("type","object","description","Optional: {orientation?,header?,footer?}")
                ),
                "required", List.of("path","content")
            ))
            .hitl(false).tags(List.of("office","docx")).riskLevel("low").build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) return ToolResult.error("path is required");
            List<Map<String, Object>> content = (List<Map<String, Object>>) arguments.get("content");
            if (content == null) return ToolResult.error("content is required");

            var parent = Paths.get(path).getParent();
            if (parent != null) Files.createDirectories(parent);

            try (XWPFDocument doc = new XWPFDocument()) {
                var pageSetup = (Map<String, Object>) arguments.get("page_setup");
                if (pageSetup != null) applyPageSetup(doc, pageSetup);

                for (var item : content) {
                    String type = (String) item.get("type");
                    if (type == null) continue;
                    switch (type) {
                        case "heading"    -> addHeading(doc, item);
                        case "paragraph"  -> addParagraph(doc, item);
                        case "table"      -> addTable(doc, item);
                        case "list"       -> addList(doc, item);
                        case "image"      -> addImage(doc, item);
                        case "page_break" -> doc.createParagraph().createRun().addBreak(BreakType.PAGE);
                        default -> log.warn("create_docx: unknown type '{}'", type);
                    }
                }
                try (var fos = new FileOutputStream(path)) { doc.write(fos); }
            }
            return ToolResult.success("Created: " + path);
        });
    }

    private void addHeading(XWPFDocument doc, Map<String, Object> item) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading" + toInt(item.get("level"), 1));
        p.createRun().setText(str(item.get("text"), ""));
    }

    private void addParagraph(XWPFDocument doc, Map<String, Object> item) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(switch(str(item.get("align"),"left").toLowerCase()) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right"  -> ParagraphAlignment.RIGHT;
            case "both"   -> ParagraphAlignment.BOTH;
            default       -> ParagraphAlignment.LEFT;
        });
        p.createRun().setText(str(item.get("text"), ""));
    }

    @SuppressWarnings("unchecked")
    private void addTable(XWPFDocument doc, Map<String, Object> item) {
        List<String> header = (List<String>) item.get("header");
        List<List<String>> rows = (List<List<String>>) item.get("rows");
        int cols = header != null ? header.size() : (rows != null && !rows.isEmpty() ? rows.get(0).size() : 1);
        int totalRows = (header != null ? 1 : 0) + (rows != null ? rows.size() : 0);
        XWPFTable table = doc.createTable(totalRows, cols);
        int ri = 0;
        if (header != null) {
            XWPFTableRow hr = table.getRow(ri++);
            for (int c = 0; c < header.size(); c++) {
                hr.getCell(c).setText(header.get(c));
                hr.getCell(c).getParagraphs().get(0).getRuns().forEach(r -> r.setBold(true));
            }
        }
        if (rows != null) for (var dr : rows) {
            XWPFTableRow tr = table.getRow(ri++);
            for (int c = 0; c < dr.size() && c < tr.getTableCells().size(); c++)
                tr.getCell(c).setText(dr.get(c));
        }
    }

    @SuppressWarnings("unchecked")
    private void addList(XWPFDocument doc, Map<String, Object> item) {
        List<String> items = (List<String>) item.get("items");
        if (items == null) return;
        for (var listItem : items) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("• " + listItem);
        }
    }

    private void addImage(XWPFDocument doc, Map<String, Object> item) {
        String imgPath = str(item.get("path"), null);
        if (imgPath == null) { log.warn("create_docx image: path is required"); return; }
        int w = toInt(item.get("width"), 400), h = toInt(item.get("height"), 300);
        try (InputStream is = new FileInputStream(imgPath)) {
            String lower = imgPath.toLowerCase();
            int type = lower.endsWith(".png") ? XWPFDocument.PICTURE_TYPE_PNG :
                       lower.endsWith(".gif") ? XWPFDocument.PICTURE_TYPE_GIF :
                       XWPFDocument.PICTURE_TYPE_JPEG;
            doc.createParagraph().createRun().addPicture(is, type, Paths.get(imgPath).getFileName().toString(),
                    Units.toEMU(w), Units.toEMU(h));
        } catch (Exception e) { log.warn("create_docx: image embed failed {}: {}", imgPath, e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private void applyPageSetup(XWPFDocument doc, Map<String, Object> ps) {
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        if ("landscape".equalsIgnoreCase(str(ps.get("orientation"), ""))) {
            pgSz.setOrient(STPageOrientation.LANDSCAPE);
            pgSz.setW(BigInteger.valueOf(15840)); pgSz.setH(BigInteger.valueOf(12240));
        } else {
            pgSz.setOrient(STPageOrientation.PORTRAIT);
            pgSz.setW(BigInteger.valueOf(12240)); pgSz.setH(BigInteger.valueOf(15840));
        }
        String headerText = str(ps.get("header"), null);
        if (headerText != null) {
            XWPFHeader hdr = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph hp = hdr.getParagraphs().isEmpty() ? hdr.createParagraph() : hdr.getParagraphs().get(0);
            hp.createRun().setText(headerText);
        }
        String footerText = str(ps.get("footer"), null);
        if (footerText != null) {
            XWPFFooter ftr = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph fp = ftr.getParagraphs().isEmpty() ? ftr.createParagraph() : ftr.getParagraphs().get(0);
            fp.setAlignment(ParagraphAlignment.CENTER);
            String[] parts = footerText.split("\\{n\\}", -1);
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) fp.createRun().setText(parts[i]);
                if (i < parts.length - 1) {
                    fp.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
                    fp.createRun().getCTR().addNewInstrText().setStringValue(" PAGE \\* MERGEFORMAT ");
                    fp.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
                }
            }
        }
    }

    private String str(Object v, String def) { return v instanceof String s ? s : def; }
    private int toInt(Object v, int def) { return v instanceof Number n ? n.intValue() : def; }
}
```

- [ ] **Step 4: Run tests — confirm pass**
```bash
mvn test -pl . -Dtest=DocxCreateToolTest -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateTool.java \
        src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxCreateToolTest.java
git commit -m "feat(office): add create_docx Java tool via Apache POI OOXML"
```

---

### Task 4: DocxReadTool tests + implementation

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxReadToolTest.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxReadTool.java`

- [ ] **Step 1: Create test file**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DocxReadToolTest {
    private final DocxReadTool tool = new DocxReadTool();

    private Path makeDoc(Path dir) throws Exception {
        Path p = dir.resolve("t.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Hello World");
            doc.createParagraph().createRun().setText("Second paragraph");
            try (var out = new FileOutputStream(p.toFile())) { doc.write(out); }
        }
        return p;
    }

    @Test void missingPath_error() { assertFalse(tool.execute(Map.of()).block().isSuccess()); }

    @Test void extractsText(@TempDir Path tmp) throws Exception {
        ToolResult r = tool.execute(Map.of("path", makeDoc(tmp).toString())).block();
        assertTrue(r.isSuccess());
        assertTrue(r.getContent().contains("Hello World"));
        assertTrue(r.getContent().contains("Second paragraph"));
    }
}
```

- [ ] **Step 2: Confirm failure**
```bash
mvn test -pl . -Dtest=DocxReadToolTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create DocxReadTool.java**

```java
package com.jaguarliu.ai.tools.builtin.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.util.*;

@Slf4j
@Component
public class DocxReadTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("read_docx")
            .description("Read a Word (.docx) file and return its paragraphs and tables as JSON.")
            .parameters(Map.of(
                "type","object",
                "properties", Map.of("path", Map.of("type","string","description","Path to .docx file")),
                "required", List.of("path")
            ))
            .hitl(false).tags(List.of("office","docx")).riskLevel("low").build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) return ToolResult.error("path is required");

            try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path))) {
                List<Map<String, Object>> content = new ArrayList<>();
                for (IBodyElement el : doc.getBodyElements()) {
                    if (el instanceof XWPFParagraph p) {
                        String text = p.getText();
                        if (text.isBlank()) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        String style = p.getStyle();
                        if (style != null && style.startsWith("Heading")) {
                            item.put("type","heading");
                            item.put("level", style.replace("Heading","").trim());
                        } else { item.put("type","paragraph"); }
                        item.put("text", text);
                        content.add(item);
                    } else if (el instanceof XWPFTable t) {
                        List<List<String>> rows = new ArrayList<>();
                        for (var row : t.getRows()) {
                            List<String> cells = new ArrayList<>();
                            for (var cell : row.getTableCells()) cells.add(cell.getText());
                            rows.add(cells);
                        }
                        content.add(Map.of("type","table","rows",rows));
                    }
                }
                return ToolResult.success(MAPPER.writeValueAsString(Map.of("content", content)));
            }
        });
    }
}
```

- [ ] **Step 4: Run all 4 office tests**
```bash
mvn test -pl . -Dtest="XlsxCreateToolTest,XlsxReadToolTest,DocxCreateToolTest,DocxReadToolTest" 2>&1 | grep -E "Tests run:|BUILD"
```
Expected: all tests pass, BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/office/DocxReadTool.java \
        src/test/java/com/jaguarliu/ai/tools/builtin/office/DocxReadToolTest.java
git commit -m "feat(office): add read_docx Java tool"
```

---

## Chunk 3: Update SKILL.md files and remove scripts

### Task 5: Rewrite xlsx/SKILL.md

**Files:**
- Modify: `.jaguarclaw/skills/xlsx/SKILL.md`

- [ ] **Step 1: Remove Python/LibreOffice execution workflow. Update allowed-tools. Keep professional standards.**

In the frontmatter, set:
```yaml
allowed-tools: [create_xlsx, read_xlsx, read_file, write_file]
```
Remove any `primaryEnv`, `requires.bins`, `requires.env` referring to Python.

Replace all sections starting from "## XLSX Creation" (or wherever Python code/scripts are) with:

```markdown
## XLSX Creation

Use the `create_xlsx` tool. Provide the full workbook as a JSON spec.

### Cell definition properties
- `value` — string, number, or boolean
- `formula` — Excel formula (e.g. `"SUM(A1:A10)"`)
- `format` — number format (e.g. `"$#,##0"`, `"0.0%"`, `"#,##0;(#,##0);-"`)
- `bold`, `italic` — boolean
- `bg_color` — 6-digit hex string (no #) — fills cell background
- `fg_color` — 6-digit hex string — sets font color
- `align` — `"left"` | `"center"` | `"right"`
- `border` — boolean — thin border all sides
- `colspan`, `rowspan` — integer — merge cells

### Color conventions (apply always unless template overrides)
- Blue text `"fg_color": "0000FF"` — hardcoded inputs
- Black text — formulas (default, no fg_color needed)
- Green text `"fg_color": "008000"` — cross-sheet links
- Red text `"fg_color": "FF0000"` — external file links
- Yellow background `"bg_color": "FFFF00"` — assumptions cells

### Example call
```json
{
  "path": "/workspace/model.xlsx",
  "sheets": [{
    "name": "Summary",
    "freeze_row": 1,
    "column_widths": [30, 15, 15, 15],
    "rows": [
      [{"value":"Item","bold":true,"bg_color":"4472C4","fg_color":"FFFFFF"},
       {"value":"FY2024","bold":true,"bg_color":"4472C4","fg_color":"FFFFFF"}],
      [{"value":"Revenue"},{"value":1000000,"format":"$#,##0"}],
      [{"value":"Growth","bold":true},{"formula":"(B3-B2)/B2","format":"0.0%"}]
    ]
  }]
}
```

## XLSX Reading and Analysis

Use `read_xlsx` to read an existing file before editing.

```json
{"path": "/workspace/data.xlsx", "sheet": "Sheet1"}
```

Returns `{"sheets": [...], "data": {"Sheet1": [[...], ...]}}`.

## Editing Existing Files

1. `read_xlsx` to understand current structure  
2. Rebuild full workbook with `create_xlsx` incorporating the changes  
3. Write to same path to overwrite

## Validation Workflow

After `create_xlsx`:
- Verify zero formula errors in all cells  
- Confirm column widths, freeze rows, number formats match spec  
- Spot-check formula cell values against expected results
```

- [ ] **Step 2: Commit**
```bash
git add .jaguarclaw/skills/xlsx/SKILL.md
git commit -m "feat(skills): rewrite xlsx skill to use Java create_xlsx/read_xlsx tools"
```

---

### Task 6: Rewrite docx/SKILL.md

**Files:**
- Modify: `.jaguarclaw/skills/docx/SKILL.md`

- [ ] **Step 1: Replace Node.js/Python execution sections. Update allowed-tools.**

Set frontmatter:
```yaml
allowed-tools: [create_docx, read_docx, read_file, write_file]
```
Remove `primaryEnv`, `requires.bins` (node, python, pandoc, LibreOffice).

Replace all execution sections with:

```markdown
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
```

- [ ] **Step 2: Commit**
```bash
git add .jaguarclaw/skills/docx/SKILL.md
git commit -m "feat(skills): rewrite docx skill to use Java create_docx/read_docx tools"
```

---

### Task 7: Delete Python/Node.js scripts

- [ ] **Step 1: Delete script directories**
```bash
rm -rf .jaguarclaw/skills/xlsx/scripts/
rm -rf .jaguarclaw/skills/docx/scripts/
```

- [ ] **Step 2: Verify no stale script references in SKILL.md**
```bash
grep -r "scripts/" .jaguarclaw/skills/xlsx/SKILL.md .jaguarclaw/skills/docx/SKILL.md && echo "FOUND - fix before committing" || echo "Clean"
```
Expected: `Clean`

- [ ] **Step 3: Commit**
```bash
git add -A .jaguarclaw/skills/xlsx/ .jaguarclaw/skills/docx/
git commit -m "chore(skills): remove xlsx/docx Python and Node.js scripts"
```

---

## Final Verification

- [ ] Full compile
```bash
mvn compile -q && echo OK
```

- [ ] All office tests
```bash
mvn test -pl . -Dtest="XlsxCreateToolTest,XlsxReadToolTest,DocxCreateToolTest,DocxReadToolTest" 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] Verify 4 new tool files exist
```bash
ls src/main/java/com/jaguarliu/ai/tools/builtin/office/
```
Expected: `DocxCreateTool.java  DocxReadTool.java  XlsxCreateTool.java  XlsxReadTool.java`

- [ ] Push
```bash
git push
```
