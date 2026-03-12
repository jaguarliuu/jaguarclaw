package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DocxCreateToolTest {

    @TempDir
    Path tmp;

    private DocxCreateTool tool() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace(tmp.toString());
        return new DocxCreateTool(props);
    }

    @Test void missingPath_error() {
        assertFalse(tool().execute(Map.of("content", List.of())).block().isSuccess());
    }

    @Test void headingAndParagraph() throws Exception {
        ToolResult r = tool().execute(Map.of(
            "path", "t.docx",
            "content", List.of(
                Map.of("type","heading","level",1,"text","Title"),
                Map.of("type","paragraph","text","Body text.")
            )
        )).block();
        assertTrue(r.isSuccess(), r.getContent());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(tmp.resolve("t.docx").toFile()))) {
            assertTrue(doc.getParagraphs().size() >= 2);
            assertEquals("Title", doc.getParagraphs().get(0).getText());
        }
    }

    @Test void tableWithHeaderAndRows() throws Exception {
        ToolResult r = tool().execute(Map.of(
            "path", "t.docx",
            "content", List.of(Map.of(
                "type","table",
                "header", List.of("Name","Score"),
                "rows", List.of(List.of("Alice","95"), List.of("Bob","87"))
            ))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(tmp.resolve("t.docx").toFile()))) {
            assertEquals(1, doc.getTables().size());
            XWPFTable t = doc.getTables().get(0);
            assertEquals(3, t.getNumberOfRows());
            assertEquals("Name",  t.getRow(0).getCell(0).getText());
            assertEquals("Alice", t.getRow(1).getCell(0).getText());
        }
    }

    @Test void listItems() throws Exception {
        ToolResult r = tool().execute(Map.of(
            "path", "t.docx",
            "content", List.of(Map.of("type","list","items",List.of("A","B"),"ordered",false))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(tmp.resolve("t.docx").toFile()))) {
            assertTrue(doc.getParagraphs().size() >= 2);
        }
    }

    @Test void headerAndFooter() throws Exception {
        ToolResult r = tool().execute(Map.of(
            "path", "t.docx",
            "page_setup", Map.of("header","Company","footer","Page {n}"),
            "content", List.of(Map.of("type","paragraph","text","Body"))
        )).block();
        assertTrue(r.isSuccess());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(tmp.resolve("t.docx").toFile()))) {
            assertFalse(doc.getHeaderList().isEmpty());
            assertTrue(doc.getHeaderList().get(0).getText().contains("Company"));
        }
    }
}
