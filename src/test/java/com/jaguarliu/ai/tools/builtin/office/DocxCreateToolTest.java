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
