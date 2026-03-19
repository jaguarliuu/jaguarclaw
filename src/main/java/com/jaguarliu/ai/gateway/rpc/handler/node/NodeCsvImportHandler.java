package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeEntity;
import com.jaguarliu.ai.nodeconsole.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeCsvImportHandler implements RpcHandler {

    static final String[] EXPECTED_HEADERS = {
            "alias", "displayname", "host", "port", "username", "tags", "safetypolicy"
    };
    static final int MAX_ROWS = 500;
    static final int MAX_BYTES = 512 * 1024;
    private static final Set<String> VALID_POLICIES = Set.of("strict", "standard", "relaxed");

    private final NodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() {
        return "nodes.import";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String csv = params != null ? (String) params.get("csv") : null;
            if (csv == null || csv.isBlank()) {
                return RpcResponse.success(request.getId(), payloadError("CSV content is empty"));
            }
            return doImport(request.getId(), csv);
        });
    }

    private RpcResponse doImport(String requestId, String csv) {
        if (csv.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return RpcResponse.success(requestId, payloadError("CSV exceeds 512 KB limit"));
        }

        List<String> lines = Arrays.stream(csv.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return RpcResponse.success(requestId, payloadError("CSV content is empty"));
        }

        String[] headerNorm = Arrays.stream(lines.get(0).split(",", -1))
                .map(col -> col.trim().toLowerCase(Locale.ROOT))
                .toArray(String[]::new);
        if (!Arrays.equals(headerNorm, EXPECTED_HEADERS)) {
            return RpcResponse.success(requestId, payloadError(
                    "Invalid header. Expected: alias,displayName,host,port,username,tags,safetyPolicy"));
        }

        List<String> dataLines = lines.subList(1, lines.size());
        if (dataLines.size() > MAX_ROWS) {
            return RpcResponse.success(requestId, payloadError("CSV exceeds 500 row limit"));
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<NodeEntity> toInsert = new ArrayList<>();
        List<String> skippedAliases = new ArrayList<>();
        Set<String> seenHosts = new HashSet<>();
        Set<String> seenAliases = new HashSet<>();

        for (int i = 0; i < dataLines.size(); i++) {
            int rowNum = i + 2;
            String[] cols = dataLines.get(i).split(",", -1);
            if (cols.length != 7) {
                errors.add(rowError(rowNum, null, null, "Expected 7 columns, got " + cols.length));
                continue;
            }

            String alias = cols[0].trim();
            String displayName = cols[1].trim();
            String host = cols[2].trim();
            String portStr = cols[3].trim();
            String username = cols[4].trim();
            String tags = cols[5].trim();
            String policyStr = cols[6].trim();

            List<Map<String, Object>> rowErrors = new ArrayList<>();

            if (alias.isBlank()) {
                rowErrors.add(rowError(rowNum, "alias", alias, "Alias is required"));
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                rowErrors.add(rowError(rowNum, "host", host, "Host is required"));
            } else if ("localhost".equals(normalizedHost)
                    || "127.0.0.1".equals(normalizedHost)
                    || "::1".equals(normalizedHost)) {
                rowErrors.add(rowError(rowNum, "host", host, "Host cannot be localhost or 127.0.0.1"));
            }

            Integer port = null;
            if (portStr.isBlank()) {
                rowErrors.add(rowError(rowNum, "port", portStr, "Port is required"));
            } else {
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        rowErrors.add(rowError(rowNum, "port", portStr, "Port must be between 1 and 65535"));
                        port = null;
                    }
                } catch (NumberFormatException e) {
                    rowErrors.add(rowError(rowNum, "port", portStr, "Port must be a number"));
                }
            }

            if (username.isBlank()) {
                rowErrors.add(rowError(rowNum, "username", username, "Username is required"));
            } else if ("root".equals(username)) {
                rowErrors.add(rowError(rowNum, "username", username, "Username cannot be 'root'"));
            }

            String resolvedPolicy = "standard";
            if (!policyStr.isBlank()) {
                if (!VALID_POLICIES.contains(policyStr)) {
                    rowErrors.add(rowError(rowNum, "safetyPolicy", policyStr,
                            "Must be strict, standard, or relaxed"));
                } else {
                    resolvedPolicy = policyStr;
                }
            }

            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                continue;
            }

            if (seenHosts.contains(host) || seenAliases.contains(alias)) {
                skippedAliases.add(alias);
                continue;
            }
            if (nodeRepository.existsByHost(host) || nodeRepository.existsByAlias(alias)) {
                skippedAliases.add(alias);
                continue;
            }

            seenHosts.add(host);
            seenAliases.add(alias);

            toInsert.add(NodeEntity.builder()
                    .alias(alias)
                    .displayName(displayName.isEmpty() ? null : displayName)
                    .connectorType("ssh")
                    .host(host)
                    .port(port)
                    .username(username)
                    .authType(null)
                    .encryptedCredential("")
                    .credentialIv("")
                    .tags(tags.isEmpty() ? null : tags.replace("|", ","))
                    .safetyPolicy(resolvedPolicy)
                    .build());
        }

        if (!toInsert.isEmpty()) {
            nodeRepository.saveAll(toInsert);
            log.info("[NodeImport] Imported {} SSH nodes, skipped {}, errors {}",
                    toInsert.size(), skippedAliases.size(), errors.size());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", toInsert.size());
        result.put("skipped", skippedAliases.size());
        result.put("skippedAliases", skippedAliases);
        result.put("errors", errors);
        return RpcResponse.success(requestId, result);
    }

    private static Map<String, Object> payloadError(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", 0);
        result.put("skipped", 0);
        result.put("skippedAliases", List.of());
        result.put("errors", List.of(rowError(0, null, null, reason)));
        return result;
    }

    private static Map<String, Object> rowError(int row, String field, String value, String reason) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("row", row);
        if (field != null) {
            error.put("field", field);
        }
        if (value != null) {
            error.put("value", value);
        }
        error.put("reason", reason);
        return error;
    }
}
