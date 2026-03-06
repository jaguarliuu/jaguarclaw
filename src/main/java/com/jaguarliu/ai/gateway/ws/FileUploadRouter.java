package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
import com.jaguarliu.ai.session.SessionFileService;
import com.jaguarliu.ai.session.SessionService;
import com.jaguarliu.ai.tools.ToolsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 简化版文件上传接口。
 * <p>
 * 仅负责将浏览器选取的文件保存到当前 agent workspace 的 uploads 目录并返回相对路径，
 * 后续由 Agent 通过 read_file 工具按需读取（支持 Tika/POI 解析二进制文档）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileUploadRouter {

    private final ToolsProperties toolsProperties;
    private final SessionService sessionService;
    private final AgentWorkspaceResolver agentWorkspaceResolver;
    private final SessionFileService sessionFileService;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".txt", ".md", ".xlsx", ".pptx",
            ".csv", ".json", ".yaml", ".yml", ".xml", ".html",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"
    );

    @Bean
    public RouterFunction<ServerResponse> fileUploadRoute() {
        return RouterFunctions.route()
                .POST("/api/files", request -> request.multipartData().flatMap(parts -> {
                    try {
                        Part filePart = parts.getFirst("file");
                        if (!(filePart instanceof FilePart fp)) {
                            return ServerResponse.badRequest().bodyValue(Map.of("error", "Missing file"));
                        }
                        String sessionId = readOptionalTextPart(parts.getFirst("sessionId"));
                        String agentId = readOptionalTextPart(parts.getFirst("agentId"));

                        String filename = fp.filename();
                        String ext = getExtension(filename);
                        if (!isAllowedExtension(ext)) {
                            return ServerResponse.badRequest().bodyValue(
                                    Map.of("error", "File type not allowed: " + ext));
                        }

                        return DataBufferUtils.join(fp.content()).flatMap(dataBuffer -> {
                            byte[] fileBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(fileBytes);
                            DataBufferUtils.release(dataBuffer);

                            if (fileBytes.length > toolsProperties.getMaxFileSize()) {
                                return ServerResponse.badRequest().bodyValue(
                                        Map.of("error", "File too large: " + fileBytes.length + " bytes"));
                            }

                            try {
                                Path uploadRoot = resolveUploadRoot(sessionId, agentId);
                                Path uploadsDir = uploadRoot.resolve(toolsProperties.getUploadDir());
                                Files.createDirectories(uploadsDir);

                                String safeFilename = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFilename(filename);
                                Path targetPath = uploadsDir.resolve(safeFilename);
                                Files.write(targetPath, fileBytes);

                                String relativePath = toolsProperties.getUploadDir() + "/" + safeFilename;
                                String mimeType = detectMimeType(filename);

                                if (sessionId != null) {
                                    sessionFileService.record(sessionId, null, relativePath, filename, fileBytes.length, mimeType);
                                }

                                log.info("File saved: {} ({} bytes) -> {} [sessionId={}, agentId={}, uploadRoot={}, mimeType={}]",
                                        filename, fileBytes.length, relativePath, sessionId, agentId, uploadRoot, mimeType);

                                return ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of(
                                                "filePath", relativePath,
                                                "filename", filename,
                                                "size", fileBytes.length,
                                                "mimeType", mimeType
                                        ));
                            } catch (IllegalArgumentException e) {
                                return ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage()));
                            } catch (Exception e) {
                                log.error("Failed to save file: {}", filename, e);
                                return ServerResponse.status(500).bodyValue(Map.of("error", e.getMessage()));
                            }
                        });
                    } catch (Exception e) {
                        log.error("File upload error", e);
                        return ServerResponse.status(500).bodyValue(Map.of("error", e.getMessage()));
                    }
                }))
                .build();
    }

    Path resolveUploadRoot(String sessionId, String agentId) {
        String normalizedSessionId = trimToNull(sessionId);
        if (normalizedSessionId != null) {
            String resolvedAgentId = sessionService.get(normalizedSessionId)
                    .map(session -> agentWorkspaceResolver.normalizeAgentId(session.getAgentId()))
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + normalizedSessionId));
            return agentWorkspaceResolver.resolveAgentWorkspace(resolvedAgentId);
        }

        String normalizedAgentId = trimToNull(agentId);
        if (normalizedAgentId != null) {
            String resolvedAgentId = agentWorkspaceResolver.normalizeAgentId(normalizedAgentId);
            return agentWorkspaceResolver.resolveAgentWorkspace(resolvedAgentId);
        }

        return Path.of(toolsProperties.getWorkspace()).toAbsolutePath().normalize();
    }

    static boolean isAllowedExtension(String extension) {
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    static String detectMimeType(String filename) {
        return switch (getExtension(filename)) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            case ".pdf" -> "application/pdf";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".csv" -> "text/csv";
            case ".json" -> "application/json";
            case ".yaml", ".yml" -> "application/yaml";
            case ".xml" -> "application/xml";
            case ".html" -> "text/html";
            case ".md" -> "text/markdown";
            case ".txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static String readOptionalTextPart(Part part) {
        if (part instanceof FormFieldPart formFieldPart) {
            return trimToNull(formFieldPart.value());
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\u4e00-\u9fa5-]", "_");
    }
}
