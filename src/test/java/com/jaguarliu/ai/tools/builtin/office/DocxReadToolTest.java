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
