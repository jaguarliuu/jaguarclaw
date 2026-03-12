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
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocxReadTool implements Tool {

    private final ToolsProperties properties;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
            .name("read_docx")
            .description("Read a Word (.docx) file and return its paragraphs and tables as JSON.")
            .parameters(Map.of(
                "type","object",
                "properties", Map.of("path", Map.of("type","string","description","Workspace-relative path to .docx file")),
                "required", List.of("path")
            ))
            .hitl(false)
            .skillScopedOnly(true)
            .tags(List.of("office","docx"))
            .riskLevel("low")
            .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathArg = (String) arguments.get("path");
            if (pathArg == null || pathArg.isBlank()) return ToolResult.error("path is required");

            // 解析并校验工作区路径安全边界
            Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
            Path filePath = workspacePath.resolve(pathArg).normalize();
            if (!filePath.startsWith(workspacePath)) {
                log.warn("read_docx path traversal attempt: {}", pathArg);
                return ToolResult.error("Access denied: path must be within workspace. Attempted: " + pathArg,
                        RuntimeFailureCategories.HARD_ENVIRONMENT_BLOCK);
            }

            try (var fis = new FileInputStream(filePath.toFile());
                 XWPFDocument doc = new XWPFDocument(fis)) {
                List<Map<String, Object>> content = new ArrayList<>();
                for (IBodyElement el : doc.getBodyElements()) {
                    if (el instanceof XWPFParagraph p) {
                        String text = p.getText();
                        if (text.isBlank()) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        String style = p.getStyle();
                        if (style != null && style.startsWith("Heading")) {
                            item.put("type","heading");
                            try {
                                item.put("level", Integer.parseInt(style.replace("Heading","").trim()));
                            } catch (NumberFormatException e) {
                                item.put("level", 1);
                            }
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
            } catch (java.io.FileNotFoundException e) {
                return ToolResult.error("File not found: " + pathArg);
            } catch (java.io.IOException e) {
                return ToolResult.error("Failed to read file: " + e.getMessage());
            }
        });
    }
}
