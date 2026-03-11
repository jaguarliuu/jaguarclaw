package com.jaguarliu.ai.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档图片存取接口。
 * POST /api/doc-images  — 上传图片，保存到 ~/.jaguarclaw/doc-images/，返回 { id, url }
 * GET  /api/doc-images/{id} — 按 id 回传图片内容
 */
@Slf4j
@Configuration
public class DocImageRouter {

    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp");
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024; // 20 MB

    private Path imagesDir() {
        Path dir = Path.of(System.getProperty("user.home"), ".jaguarclaw", "doc-images");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    @Bean
    public RouterFunction<ServerResponse> docImageRoutes() {
        return RouterFunctions.route()
                // ── Upload ──────────────────────────────────────────────────
                .POST("/api/doc-images", request -> request.multipartData().flatMap(parts -> {
                    Part filePart = parts.getFirst("file");
                    if (!(filePart instanceof FilePart fp)) {
                        return ServerResponse.badRequest().bodyValue(Map.of("error", "Missing file part"));
                    }
                    String filename = fp.filename();
                    String ext = extension(filename).toLowerCase();
                    if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
                        return ServerResponse.badRequest().bodyValue(Map.of("error", "Not an allowed image type: " + ext));
                    }

                    return DataBufferUtils.join(fp.content()).flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        if (bytes.length > MAX_IMAGE_BYTES) {
                            return ServerResponse.badRequest().bodyValue(Map.of("error", "Image too large (max 20 MB)"));
                        }

                        try {
                            String id = UUID.randomUUID().toString().replace("-", "") + ext;
                            Path target = imagesDir().resolve(id);
                            Files.write(target, bytes);
                            log.info("doc-image saved: {} ({} bytes)", id, bytes.length);
                            return ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Map.of("id", id, "url", "/api/doc-images/" + id));
                        } catch (Exception e) {
                            log.error("Failed to save doc image", e);
                            return ServerResponse.status(500).bodyValue(Map.of("error", e.getMessage()));
                        }
                    });
                }))

                // ── Serve ───────────────────────────────────────────────────
                .GET("/api/doc-images/{id}", request -> {
                    String id = request.pathVariable("id");
                    // Prevent path traversal
                    if (id.contains("/") || id.contains("\\") || id.contains("..")) {
                        return ServerResponse.badRequest().build();
                    }
                    Path file = imagesDir().resolve(id);
                    if (!Files.exists(file) || !Files.isRegularFile(file)) {
                        return ServerResponse.notFound().build();
                    }
                    try {
                        byte[] bytes = Files.readAllBytes(file);
                        MediaType mediaType = mediaTypeFor(extension(id));
                        return ServerResponse.ok()
                                .contentType(mediaType)
                                .bodyValue(bytes);
                    } catch (Exception e) {
                        log.error("Failed to serve doc image: {}", id, e);
                        return ServerResponse.status(500).build();
                    }
                })
                .build();
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private static MediaType mediaTypeFor(String ext) {
        return switch (ext.toLowerCase()) {
            case ".png"  -> MediaType.IMAGE_PNG;
            case ".gif"  -> MediaType.IMAGE_GIF;
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            case ".webp" -> MediaType.parseMediaType("image/webp");
            case ".bmp"  -> MediaType.parseMediaType("image/bmp");
            default      -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
