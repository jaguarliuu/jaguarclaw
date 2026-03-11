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
