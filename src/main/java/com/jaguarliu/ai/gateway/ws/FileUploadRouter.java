package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.agents.context.AgentWorkspaceResolver;
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

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".txt", ".md", ".xlsx", ".pptx",
            ".csv", ".json", ".yaml", ".yml", ".xml", ".html"
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

                        // 验证扩展名
                        String ext = getExtension(filename);
                        if (!ALLOWED_EXTENSIONS.contains(ext)) {
                            return ServerResponse.badRequest().bodyValue(
                                    Map.of("error", "File type not allowed: " + ext));
                        }

                        // 读取文件内容
                        return DataBufferUtils.join(fp.content()).flatMap(dataBuffer -> {
                            byte[] fileBytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(fileBytes);
                            DataBufferUtils.release(dataBuffer);

                            // 验证大小
                            if (fileBytes.length > toolsProperties.getMaxFileSize()) {
                                return ServerResponse.badRequest().bodyValue(
                                        Map.of("error", "File too large: " + fileBytes.length + " bytes"));
                            }

                            try {
                                // 优先保存到 session/agent 对应 workspace 下，缺省回退全局 workspace
                                Path uploadRoot = resolveUploadRoot(sessionId, agentId);
                                Path uploadsDir = uploadRoot.resolve(toolsProperties.getUploadDir());
                                Files.createDirectories(uploadsDir);

                                // 生成唯一文件名
                                String safeFilename = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFilename(filename);
                                Path targetPath = uploadsDir.resolve(safeFilename);
                                Files.write(targetPath, fileBytes);

                                // 返回相对于 workspace 的路径（供 read_file 使用）
                                String relativePath = toolsProperties.getUploadDir() + "/" + safeFilename;

                                log.info("File saved: {} ({} bytes) -> {} [sessionId={}, agentId={}, uploadRoot={}]",
                                        filename, fileBytes.length, relativePath, sessionId, agentId, uploadRoot);

                                return ServerResponse.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(Map.of(
                                                "filePath", relativePath,
                                                "filename", filename,
                                                "size", fileBytes.length
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
        // 保留字母、数字、点、下划线、中文等，替换其它字符
        return filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }
}
