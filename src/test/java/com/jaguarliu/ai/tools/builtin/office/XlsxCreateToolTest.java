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
