package com.jaguarliu.ai.tools.builtin.office;

import com.jaguarliu.ai.runtime.RuntimeFailureCategories;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
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
@RequiredArgsConstructor
public class DocxCreateTool implements Tool {

    private final ToolsProperties properties;

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
                    "path",       Map.of("type","string","description","Output .docx file path (workspace-relative, e.g. \"report.docx\")"),
                    "content",    Map.of("type","array","description","Array of content items (heading/paragraph/table/list/image/page_break)"),
                    "page_setup", Map.of("type","object","description","Optional: {orientation?,header?,footer?}")
                ),
                "required", List.of("path","content")
            ))
            .hitl(false)
            .skillScopedOnly(true)
            .producesFile(true)
            .tags(List.of("office","docx"))
            .riskLevel("low")
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathArg = (String) arguments.get("path");
            if (pathArg == null || pathArg.isBlank()) return ToolResult.error("path is required");
            List<Map<String, Object>> content = (List<Map<String, Object>>) arguments.get("content");
            if (content == null) return ToolResult.error("content is required");

            // 解析并校验工作区路径安全边界
            Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
            Path filePath = workspacePath.resolve(pathArg).normalize();
            if (!filePath.startsWith(workspacePath)) {
                log.warn("create_docx path traversal attempt: {}", pathArg);
                return ToolResult.error("Access denied: path must be within workspace. Attempted: " + pathArg,
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK);
            }

            Path parent = filePath.getParent();
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
                        case "image"      -> addImage(doc, item, workspacePath);
                        case "page_break" -> doc.createParagraph().createRun().addBreak(BreakType.PAGE);
                        default -> log.warn("create_docx: unknown type '{}'", type);
                    }
                }
                try (var fos = new FileOutputStream(filePath.toFile())) { doc.write(fos); }
            }
            return ToolResult.success("Created: " + pathArg);
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

    private void addImage(XWPFDocument doc, Map<String, Object> item, Path workspacePath) {
        String imgPathArg = str(item.get("path"), null);
        if (imgPathArg == null) { log.warn("create_docx image: path is required"); return; }
        int w = toInt(item.get("width"), 400), h = toInt(item.get("height"), 300);
        // Resolve image path relative to workspace
        Path imgPath = workspacePath.resolve(imgPathArg).normalize();
        try (InputStream is = new FileInputStream(imgPath.toFile())) {
            String lower = imgPathArg.toLowerCase();
            int type = lower.endsWith(".png") ? XWPFDocument.PICTURE_TYPE_PNG :
                       lower.endsWith(".gif") ? XWPFDocument.PICTURE_TYPE_GIF :
                       XWPFDocument.PICTURE_TYPE_JPEG;
            doc.createParagraph().createRun().addPicture(is, type, imgPath.getFileName().toString(),
                    Units.toEMU(w), Units.toEMU(h));
        } catch (Exception e) { log.warn("create_docx: image embed failed {}: {}", imgPathArg, e.getMessage()); }
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
