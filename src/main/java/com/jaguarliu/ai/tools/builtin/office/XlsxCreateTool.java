package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class XlsxCreateTool implements Tool {

    private final ToolsProperties properties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("create_xlsx")
            .description("Create an Excel (.xlsx) file from a declarative JSON spec. Supports multiple sheets, string/number/formula cells, formatting (bold, italic, bg_color, fg_color, format, align, border), column widths, freeze panes, merged cells. Formulas are pre-evaluated.")
            .parameters(Map.of(
                "type", "object",
                "properties", Map.of(
                    "path",   Map.of("type","string","description","Output .xlsx file path (workspace-relative, e.g. \"report.xlsx\")"),
                    "sheets", Map.of("type","array","description",
                        "Array of sheet defs: [{name, freeze_row?, freeze_col?, column_widths?, rows}]. " +
                        "rows is 2D array of cell defs: {value?, formula?, format?, bold?, italic?, bg_color?, fg_color?, align?, border?, colspan?, rowspan?}")
                ),
                "required", List.of("path","sheets")
            ))
            .hitl(false)
            .skillScopedOnly(true)
            .producesFile(true)
            .tags(List.of("office","xlsx"))
            .riskLevel("low")
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathArg = (String) arguments.get("path");
            if (pathArg == null || pathArg.isBlank()) return ToolResult.error("path is required");

            List<Map<String, Object>> sheets = (List<Map<String, Object>>) arguments.get("sheets");
            if (sheets == null || sheets.isEmpty()) return ToolResult.error("sheets is required and must not be empty");

            // 解析并校验工作区路径安全边界
            Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
            Path filePath = workspacePath.resolve(pathArg).normalize();
            if (!filePath.startsWith(workspacePath)) {
                log.warn("create_xlsx path traversal attempt: {}", pathArg);
                return ToolResult.error("Access denied: path must be within workspace. Attempted: " + pathArg,
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK);
            }

            Files.createDirectories(filePath.getParent() != null ? filePath.getParent() : workspacePath);

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                Map<String, XSSFCellStyle> styleCache = new HashMap<>();
                for (var sheetDef : sheets) buildSheet(workbook, sheetDef, styleCache);

                try {
                    workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
                } catch (Exception e) {
                    log.warn("Formula evaluation partial failure (non-fatal): {}", e.getMessage());
                }

                try (var fos = new FileOutputStream(filePath.toFile())) {
                    workbook.write(fos);
                }
            }
            return ToolResult.success("Created: " + pathArg);
        });
    }

    @SuppressWarnings("unchecked")
    private void buildSheet(XSSFWorkbook wb, Map<String, Object> def, Map<String, XSSFCellStyle> styleCache) {
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
                cell.setCellStyle(buildStyle(wb, cellDef, styleCache));
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

    private XSSFCellStyle buildStyle(XSSFWorkbook wb, Map<String, Object> def, Map<String, XSSFCellStyle> styleCache) {
        boolean bold   = Boolean.TRUE.equals(def.get("bold"));
        boolean italic = Boolean.TRUE.equals(def.get("italic"));
        String fgColor = (String) def.get("fg_color");
        String bgColor = (String) def.get("bg_color");
        String format  = (String) def.get("format");
        String align   = (String) def.get("align");
        boolean border = Boolean.TRUE.equals(def.get("border"));

        String key = bold + "|" + italic + "|" + fgColor + "|" + bgColor + "|" + format + "|" + align + "|" + border;
        XSSFCellStyle cached = styleCache.get(key);
        if (cached != null) return cached;

        XSSFCellStyle style = wb.createCellStyle();
        if (bold || italic || fgColor != null) {
            XSSFFont font = wb.createFont();
            font.setBold(bold);
            font.setItalic(italic);
            if (fgColor != null) font.setColor(hexColor(fgColor));
            style.setFont(font);
        }
        if (bgColor != null) {
            style.setFillForegroundColor(hexColor(bgColor));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (format != null) style.setDataFormat(wb.createDataFormat().getFormat(format));
        if (align != null) style.setAlignment(switch(align.toLowerCase()) {
            case "center" -> HorizontalAlignment.CENTER;
            case "right"  -> HorizontalAlignment.RIGHT;
            default       -> HorizontalAlignment.LEFT;
        });
        if (border) {
            style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN);
        }
        styleCache.put(key, style);
        return style;
    }

    private XSSFColor hexColor(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return new XSSFColor(new byte[]{
                (byte)Integer.parseInt(h.substring(0,2),16),
                (byte)Integer.parseInt(h.substring(2,4),16),
                (byte)Integer.parseInt(h.substring(4,6),16)
            }, null);
        } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex color value: '" + hex + "'. Expected 6 hex digits (e.g. #FF0000 or FF0000)", e);
        }
    }

    private int toInt(Object v, int def) { return v instanceof Number n ? n.intValue() : def; }
}
