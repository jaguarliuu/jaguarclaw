package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DocxReadToolTest {

    @TempDir
    Path tmp;

    private DocxReadTool tool() {
        ToolsProperties props = new ToolsProperties();
        props.setWorkspace(tmp.toString());
        return new DocxReadTool(props);
    }

    private void makeDoc() throws Exception {
        Path p = tmp.resolve("t.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Hello World");
            doc.createParagraph().createRun().setText("Second paragraph");
            try (var out = new FileOutputStream(p.toFile())) { doc.write(out); }
        }
    }

    @Test void missingPath_error() { assertFalse(tool().execute(Map.of()).block().isSuccess()); }

    @Test void extractsText() throws Exception {
        makeDoc();
        ToolResult r = tool().execute(Map.of("path", "t.docx")).block();
        assertTrue(r.isSuccess());
        assertTrue(r.getContent().contains("Hello World"));
        assertTrue(r.getContent().contains("Second paragraph"));
    }
}
