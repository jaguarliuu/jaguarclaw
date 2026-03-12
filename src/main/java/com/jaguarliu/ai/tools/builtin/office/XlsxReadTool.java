package com.jaguarliu.ai.tools.builtin.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class XlsxReadTool implements Tool {

    private final ToolsProperties properties;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("read_xlsx")
            .description("Read an Excel (.xlsx) file. Returns sheet names and cell values as JSON. Optionally restrict to one sheet.")
            .parameters(Map.of(
                "type","object",
                "properties", Map.of(
                    "path",  Map.of("type","string","description","Workspace-relative path to .xlsx file"),
                    "sheet", Map.of("type","string","description","Sheet name to read (all sheets if omitted)")
                ),
                "required", List.of("path")
            ))
            .hitl(false)
            .skillScopedOnly(true)
            .tags(List.of("office","xlsx"))
            .riskLevel("low")
            .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathArg = (String) arguments.get("path");
            if (pathArg == null || pathArg.isBlank()) return ToolResult.error("path is required");
            String targetSheet = (String) arguments.get("sheet");

            // 解析并校验工作区路径安全边界
            Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
            Path filePath = workspacePath.resolve(pathArg).normalize();
            if (!filePath.startsWith(workspacePath)) {
                log.warn("read_xlsx path traversal attempt: {}", pathArg);
                return ToolResult.error("Access denied: path must be within workspace. Attempted: " + pathArg,
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK);
            }

            try (var fis = new FileInputStream(filePath.toFile());
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {
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
            } catch (java.io.FileNotFoundException e) {
                return ToolResult.error("File not found: " + pathArg);
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
