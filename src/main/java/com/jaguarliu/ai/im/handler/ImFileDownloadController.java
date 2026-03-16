package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.im.entity.ImMessageEntity;
import com.jaguarliu.ai.im.repository.ImMessageRepository;
import com.jaguarliu.ai.im.service.ImMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ImFileDownloadController {

    private final ImMessageRepository messageRepo;
    private final ImMessagingService messagingService;

    /** HTTP multipart upload — avoids WebSocket message size limits */
    @Bean
    public RouterFunction<ServerResponse> imFileSendRoute() {
        return RouterFunctions.route()
            .POST("/api/im/files/send", request -> request.multipartData().flatMap(parts -> {
                try {
                    Part filePart = parts.getFirst("file");
                    if (!(filePart instanceof FilePart fp)) {
                        return ServerResponse.badRequest()
                            .bodyValue(Map.of("error", "Missing file part"));
                    }
                    String toNodeId = readTextPart(parts.getFirst("toNodeId"));
                    if (toNodeId == null || toNodeId.isBlank()) {
                        return ServerResponse.badRequest()
                            .bodyValue(Map.of("error", "Missing toNodeId"));
                    }
                    String filename = fp.filename().isBlank() ? "file" : fp.filename();
                    String mimeType = fp.headers().getContentType() != null
                        ? fp.headers().getContentType().toString()
                        : "application/octet-stream";

                    return DataBufferUtils.join(fp.content()).flatMap(dataBuffer -> {
                        byte[] fileBytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(fileBytes);
                        DataBufferUtils.release(dataBuffer);
                        try {
                            String messageId = messagingService.sendFile(
                                toNodeId, filename, mimeType, fileBytes);
                            return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("messageId", messageId));
                        } catch (Exception e) {
                            log.error("[IM] sendFile failed", e);
                            return ServerResponse.status(500)
                                .bodyValue(Map.of("error", e.getMessage() != null
                                    ? e.getMessage() : "Send failed"));
                        }
                    });
                } catch (Exception e) {
                    log.error("[IM] file upload error", e);
                    return ServerResponse.status(500)
                        .bodyValue(Map.of("error", e.getMessage()));
                }
            }))
            .build();
    }

    /** Download / inline-preview a file by message ID */
    @RestController
    @RequestMapping("/api/im/files")
    @RequiredArgsConstructor
    static class DownloadHandler {
        private final ImMessageRepository messageRepo;

        @GetMapping("/{messageId}")
        public ResponseEntity<Resource> downloadFile(@PathVariable String messageId) {
            Optional<ImMessageEntity> entityOpt = messageRepo.findById(messageId);
            if (entityOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            String path = entityOpt.get().getLocalFilePath();
            if (path == null || path.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            String filename = file.getName();
            String encoded  = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
            String mimeType = guessMimeType(filename);

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(file));
        }

        private static String guessMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png"))  return "image/png";
            if (lower.endsWith(".gif"))  return "image/gif";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".pdf"))  return "application/pdf";
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
            if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            if (lower.endsWith(".doc"))  return "application/msword";
            if (lower.endsWith(".txt"))  return "text/plain";
            if (lower.endsWith(".md"))   return "text/markdown";
            return "application/octet-stream";
        }
    }

    private static String readTextPart(Part part) {
        if (part instanceof FormFieldPart ffp) return ffp.value().trim();
        return null;
    }
}
