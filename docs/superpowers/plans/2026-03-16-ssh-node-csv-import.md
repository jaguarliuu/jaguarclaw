# SSH Node CSV Import Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bulk CSV import for SSH nodes, plus remove the obsolete DB connector type from backend and frontend.

**Architecture:** Two new RPC handlers (`nodes.import` and `nodes.import.template`) handle parsing and template delivery; the import handler writes directly to `NodeRepository` with empty credential placeholders, bypassing `NodeService.register`. The frontend adds an import toolbar to `NodesSection.vue` and a "待配置" badge for nodes missing credentials.

**Tech Stack:** Java 21 / Spring Boot (existing RPC pattern), JUnit 5 + Mockito (existing test pattern), Vue 3 + TypeScript (existing composable/component pattern), no new dependencies.

---

## Chunk 1: Backend

### Task 1: Add `existsByHost` to NodeRepository

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeRepository.java`

`existsByAlias` already exists. Add the parallel host query:

- [ ] **Step 1: Add method to repository interface**

Open `src/main/java/com/jaguarliu/ai/nodeconsole/NodeRepository.java`. After line 18 (`boolean existsByAlias(String alias);`), add:

```java
boolean existsByHost(String host);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/NodeRepository.java
git commit -m "feat(nodes): add existsByHost to NodeRepository"
```

---

### Task 2: NodeCsvImportHandler (TDD)

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandlerTest.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandler.java`

This handler parses a raw CSV string, validates each row, deduplicates by host and alias, and inserts valid rows as SSH nodes with empty credential placeholders.

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandlerTest.java`:

```java
package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.nodeconsole.NodeEntity;
import com.jaguarliu.ai.nodeconsole.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NodeCsvImportHandler Tests")
class NodeCsvImportHandlerTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NodeCsvImportHandler handler;

    private static final String HEADER = "alias,displayName,host,port,username,tags,safetyPolicy";
    private static final String VALID_ROW = "web-01,Web 01,192.168.1.10,22,deploy,prod|web,standard";

    @BeforeEach
    void setUp() {
        when(nodeRepository.existsByHost(anyString())).thenReturn(false);
        when(nodeRepository.existsByAlias(anyString())).thenReturn(false);
    }

    private RpcResponse call(String csv) throws Exception {
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of("csv", csv));
        RpcRequest req = RpcRequest.builder().id("r1").method("nodes.import").payload(Map.of("csv", csv)).build();
        return handler.handle("c1", req).block();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(RpcResponse r) {
        return (Map<String, Object>) r.getPayload();
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("imports valid rows and returns count")
    void importsValidRows() throws Exception {
        RpcResponse r = call(HEADER + "\n" + VALID_ROW);
        assertNull(r.getError());
        Map<String, Object> p = payload(r);
        assertEquals(1, p.get("imported"));
        assertEquals(0, p.get("skipped"));
        assertTrue(((List<?>) p.get("errors")).isEmpty());

        ArgumentCaptor<List<NodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(nodeRepository).saveAll(captor.capture());
        NodeEntity saved = captor.getValue().get(0);
        assertEquals("web-01", saved.getAlias());
        assertEquals("Web 01", saved.getDisplayName());
        assertEquals("192.168.1.10", saved.getHost());
        assertEquals(22, saved.getPort());
        assertEquals("deploy", saved.getUsername());
        assertEquals("ssh", saved.getConnectorType());
        assertNull(saved.getAuthType());
        assertEquals("", saved.getEncryptedCredential());
        assertEquals("", saved.getCredentialIv());
        assertEquals("prod,web", saved.getTags()); // pipe→comma
        assertEquals("standard", saved.getSafetyPolicy());
    }

    @Test
    @DisplayName("defaults safetyPolicy to standard when blank")
    void defaultsSafetyPolicy() throws Exception {
        call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,");
        ArgumentCaptor<List<NodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(nodeRepository).saveAll(captor.capture());
        assertEquals("standard", captor.getValue().get(0).getSafetyPolicy());
    }

    @Test
    @DisplayName("stores null tags when tags column is empty")
    void nullTagsWhenEmpty() throws Exception {
        call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");
        ArgumentCaptor<List<NodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(nodeRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getTags());
    }

    @Test
    @DisplayName("header is case-insensitive and trimmed")
    void caseInsensitiveHeader() throws Exception {
        String weirdHeader = "Alias , DisplayName , Host , Port , Username , Tags , SafetyPolicy";
        RpcResponse r = call(weirdHeader + "\n" + VALID_ROW);
        assertNull(r.getError());
        assertEquals(1, payload(r).get("imported"));
    }

    // ── Payload-level errors (row: 0) ───────────────────────────────────────

    @Test
    @DisplayName("returns row:0 error for empty CSV")
    void emptycsv() throws Exception {
        RpcResponse r = call("   ");
        assertNull(r.getError());
        List<?> errors = (List<?>) payload(r).get("errors");
        assertFalse(errors.isEmpty());
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
    }

    @Test
    @DisplayName("returns row:0 error for wrong header")
    void wrongHeader() throws Exception {
        RpcResponse r = call("wrong,header,cols\nweb-01,x,192.168.1.10,22,deploy,,standard");
        assertNull(r.getError());
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals(1, errors.size());
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("returns row:0 error when exceeding 500 rows")
    void tooManyRows() throws Exception {
        StringBuilder sb = new StringBuilder(HEADER);
        for (int i = 0; i < 501; i++) {
            sb.append("\nweb-").append(i).append(",,192.168.1.").append(i % 256).append(",22,deploy,,standard");
        }
        RpcResponse r = call(sb.toString());
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
        verify(nodeRepository, never()).saveAll(any());
    }

    // ── Per-row validation errors ────────────────────────────────────────────

    @Test
    @DisplayName("error on wrong column count")
    void wrongColumnCount() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,only-three-cols");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals(1, errors.size());
        Map<?, ?> err = (Map<?, ?>) errors.get(0);
        assertEquals(2, err.get("row"));
        assertTrue(err.get("reason").toString().contains("Expected 7 columns"));
    }

    @Test
    @DisplayName("error when alias is blank")
    void missingAlias() throws Exception {
        RpcResponse r = call(HEADER + "\n,Display,192.168.1.10,22,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("alias", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when host is blank")
    void missingHost() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,Display,,22,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "host".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when host is localhost")
    void localhostHost() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,localhost,22,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("host", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when host is 127.0.0.1")
    void loopbackHost() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,127.0.0.1,22,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("host", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when port is blank")
    void missingPort() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "port".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when port is out of range")
    void portOutOfRange() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,99999,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("port", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when port is not a number")
    void portNotNumber() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,abc,deploy,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("port", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when username is blank")
    void missingUsername() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,22,,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "username".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when username is root")
    void rootUsername() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,22,root,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("username", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when safetyPolicy is invalid")
    void invalidSafetyPolicy() throws Exception {
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,extreme");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals("safetyPolicy", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("collects all errors for a row without stopping early")
    void collectsAllRowErrors() throws Exception {
        // missing host AND root username → two errors for row 2
        RpcResponse r = call(HEADER + "\nweb-01,,,22,root,,standard");
        List<?> errors = (List<?>) payload(r).get("errors");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> "host".equals(((Map<?, ?>) e).get("field"))));
        assertTrue(errors.stream().anyMatch(e -> "username".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("partial success: valid rows imported even when other rows error")
    void partialSuccess() throws Exception {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"  // valid
                + "\n,bad,,22,root,,standard";                  // 3 errors
        RpcResponse r = call(csv);
        Map<String, Object> p = payload(r);
        assertEquals(1, p.get("imported"));
        assertFalse(((List<?>) p.get("errors")).isEmpty());
    }

    // ── Deduplication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("within-CSV host dedup: second row with same host is skipped")
    void withinCsvHostDedup() throws Exception {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"
                + "\nweb-02,,192.168.1.10,22,deploy,,standard"; // same host
        RpcResponse r = call(csv);
        Map<String, Object> p = payload(r);
        assertEquals(1, p.get("imported"));
        assertEquals(1, p.get("skipped"));
        assertTrue(((List<?>) p.get("skippedAliases")).contains("web-02"));
    }

    @Test
    @DisplayName("within-CSV alias dedup: second row with same alias is skipped")
    void withinCsvAliasDedup() throws Exception {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"
                + "\nweb-01,,192.168.1.11,22,deploy,,standard"; // same alias
        RpcResponse r = call(csv);
        Map<String, Object> p = payload(r);
        assertEquals(1, p.get("imported"));
        assertEquals(1, p.get("skipped"));
        assertTrue(((List<?>) p.get("skippedAliases")).contains("web-01"));
    }

    @Test
    @DisplayName("DB host dedup: row skipped when host already in DB")
    void dbHostDedup() throws Exception {
        when(nodeRepository.existsByHost("192.168.1.10")).thenReturn(true);
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");
        Map<String, Object> p = payload(r);
        assertEquals(0, p.get("imported"));
        assertEquals(1, p.get("skipped"));
        assertTrue(((List<?>) p.get("skippedAliases")).contains("web-01"));
        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("DB alias dedup: row skipped when alias already in DB")
    void dbAliasDedup() throws Exception {
        when(nodeRepository.existsByAlias("web-01")).thenReturn(true);
        RpcResponse r = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");
        Map<String, Object> p = payload(r);
        assertEquals(0, p.get("imported"));
        assertEquals(1, p.get("skipped"));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -Dtest=NodeCsvImportHandlerTest -q 2>&1 | tail -10
```

Expected: compilation error (class not found)

- [ ] **Step 3: Create the handler**

Create `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandler.java`:

```java
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeCsvImportHandler implements RpcHandler {

    static final String[] EXPECTED_HEADERS =
            {"alias", "displayname", "host", "port", "username", "tags", "safetypolicy"};
    static final int MAX_ROWS = 500;
    static final int MAX_BYTES = 512 * 1024;

    private final NodeRepository nodeRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getMethod() { return "nodes.import"; }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<String, Object> params = objectMapper.convertValue(request.getPayload(), Map.class);
            String csv = (String) params.get("csv");
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

        List<String> lines = Arrays.stream(csv.split("\n"))
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .collect(Collectors.toList());

        if (lines.isEmpty()) {
            return RpcResponse.success(requestId, payloadError("CSV content is empty"));
        }

        // Validate header
        String[] headerCols = lines.get(0).split(",", -1);
        String[] headerNorm = Arrays.stream(headerCols)
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
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
                errors.add(rowError(rowNum, null, null,
                        "Expected 7 columns, got " + cols.length));
                continue;
            }

            String alias       = cols[0].trim();
            String displayName = cols[1].trim();
            String host        = cols[2].trim();
            String portStr     = cols[3].trim();
            String username    = cols[4].trim();
            String tags        = cols[5].trim();
            String policyStr   = cols[6].trim();

            List<Map<String, Object>> rowErrors = new ArrayList<>();

            // alias
            if (alias.isBlank()) {
                rowErrors.add(rowError(rowNum, "alias", alias, "Alias is required"));
            }

            // host
            if (host.isBlank()) {
                rowErrors.add(rowError(rowNum, "host", host, "Host is required"));
            } else if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                rowErrors.add(rowError(rowNum, "host", host,
                        "Host cannot be localhost or 127.0.0.1"));
            }

            // port
            Integer port = null;
            if (portStr.isBlank()) {
                rowErrors.add(rowError(rowNum, "port", portStr, "Port is required"));
            } else {
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        rowErrors.add(rowError(rowNum, "port", portStr,
                                "Port must be between 1 and 65535"));
                        port = null;
                    }
                } catch (NumberFormatException e) {
                    rowErrors.add(rowError(rowNum, "port", portStr, "Port must be a number"));
                }
            }

            // username
            if (username.isBlank()) {
                rowErrors.add(rowError(rowNum, "username", username, "Username is required"));
            } else if ("root".equals(username)) {
                rowErrors.add(rowError(rowNum, "username", username,
                        "Username cannot be 'root'"));
            }

            // safetyPolicy
            String resolvedPolicy = "standard";
            if (!policyStr.isBlank()) {
                if (!Set.of("strict", "standard", "relaxed").contains(policyStr)) {
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

            // Dedup: within-CSV
            if (seenHosts.contains(host) || seenAliases.contains(alias)) {
                skippedAliases.add(alias);
                continue;
            }
            // Dedup: DB
            if (nodeRepository.existsByHost(host) || nodeRepository.existsByAlias(alias)) {
                skippedAliases.add(alias);
                continue;
            }

            seenHosts.add(host);
            seenAliases.add(alias);

            String storedTags = tags.isEmpty() ? null : tags.replace("|", ",");

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
                    .tags(storedTags)
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
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("row", row);
        if (field != null) err.put("field", field);
        if (value != null) err.put("value", value);
        err.put("reason", reason);
        return err;
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -Dtest=NodeCsvImportHandlerTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandler.java \
        src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeCsvImportHandlerTest.java
git commit -m "feat(nodes): add NodeCsvImportHandler with full TDD test coverage"
```

---

### Task 3: NodeImportTemplateHandler

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeImportTemplateHandlerTest.java`
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeImportTemplateHandler.java`

Simple handler — returns the CSV template as a string.

- [ ] **Step 1: Write failing test**

Create `src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeImportTemplateHandlerTest.java`:

```java
package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportTemplateHandlerTest {

    private final NodeImportTemplateHandler handler = new NodeImportTemplateHandler();

    @Test
    void returnsCorrectMethod() {
        assertEquals("nodes.import.template", handler.getMethod());
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsCsvWithExpectedHeader() {
        RpcRequest req = RpcRequest.builder().id("r1").method("nodes.import.template").payload(null).build();
        RpcResponse r = handler.handle("c1", req).block();
        assertNotNull(r);
        assertNull(r.getError());
        Map<String, Object> payload = (Map<String, Object>) r.getPayload();
        String csv = (String) payload.get("csv");
        assertNotNull(csv);
        assertTrue(csv.startsWith("alias,displayName,host,port,username,tags,safetyPolicy"),
                "First line must be the expected header");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -Dtest=NodeImportTemplateHandlerTest -q 2>&1 | tail -5
```

Expected: compilation error (class not found)

- [ ] **Step 3: Create the handler**

```java
package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class NodeImportTemplateHandler implements RpcHandler {

    private static final String TEMPLATE =
            "alias,displayName,host,port,username,tags,safetyPolicy\n" +
            "web-01,Web Server 01,192.168.1.10,22,deploy,prod|web,standard\n" +
            "web-02,,192.168.1.11,22,deploy,,standard";

    @Override
    public String getMethod() { return "nodes.import.template"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() ->
                RpcResponse.success(request.getId(), Map.of("csv", TEMPLATE))
        );
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -Dtest="NodeImportTemplateHandlerTest,NodeCsvImportHandlerTest,NodeTestHandlerTest,NodeValidationTest" -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeImportTemplateHandler.java \
        src/test/java/com/jaguarliu/ai/gateway/rpc/handler/node/NodeImportTemplateHandlerTest.java
git commit -m "feat(nodes): add NodeImportTemplateHandler for CSV template download"
```

---

### Task 4: Remove DB connector type from backend

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeListTool.java`
- Modify: `src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeStatusCheckTool.java`

`NodeValidator` and `NodeService` have no DB-specific branches to remove. Only the tool enum lists need updating.

- [ ] **Step 1: Remove "db" from NodeListTool**

Open `src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeListTool.java`. Make three replacements:

Replace tool description (line 30):
```java
// Before:
.description("列出所有已注册的远程节点。可按类型（ssh/k8s/db）或标签过滤。返回节点别名、类型、主机、标签、安全策略和连接状态，不包含凭据信息。")
// After:
.description("列出所有已注册的远程节点。可按类型（ssh/k8s）或标签过滤。返回节点别名、类型、主机、标签、安全策略和连接状态，不包含凭据信息。")
```

Replace parameter description (line 36):
```java
// Before:
"description", "按连接器类型过滤: ssh, k8s, db",
// After:
"description", "按连接器类型过滤: ssh, k8s",
```

Replace enum (line 37):
```java
// Before:
"enum", List.of("ssh", "k8s", "db")
// After:
"enum", List.of("ssh", "k8s")
```

- [ ] **Step 2: Remove "db" from NodeStatusCheckTool enum**

Open `src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeStatusCheckTool.java`.

Find line:
```java
"enum", List.of("ssh", "k8s", "db")
```

Replace with:
```java
"enum", List.of("ssh", "k8s")
```

- [ ] **Step 3: Run full test suite to verify no regressions**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeListTool.java \
        src/main/java/com/jaguarliu/ai/tools/builtin/node/NodeStatusCheckTool.java
git commit -m "chore(nodes): remove obsolete db connector type from tool enum lists"
```

---

## Chunk 2: Frontend

### Task 5: Update types/index.ts

**Files:**
- Modify: `jaguarclaw-ui/src/types/index.ts:325`

- [ ] **Step 1: Remove 'db' from ConnectorType and add NodeImportResult**

Open `jaguarclaw-ui/src/types/index.ts`.

Find line 325:
```ts
export type ConnectorType = 'ssh' | 'k8s' | 'db'
```

Replace with:
```ts
export type ConnectorType = 'ssh' | 'k8s'
```

Then add after the `NodeRegisterPayload` interface (search for `NodeTestResult` interface and add before it):

```ts
export interface NodeImportError {
  row: number
  field?: string
  value?: string
  reason: string
}

export interface NodeImportResult {
  imported: number
  skipped: number
  skippedAliases: string[]
  errors: NodeImportError[]
}
```

- [ ] **Step 2: Commit**

```bash
git add jaguarclaw-ui/src/types/index.ts
git commit -m "chore(types): remove db ConnectorType, add NodeImportResult types"
```

---

### Task 6: Add importNodes and downloadTemplate to useNodeConsole.ts

**Files:**
- Modify: `jaguarclaw-ui/src/composables/useNodeConsole.ts`

- [ ] **Step 1: Add import at top of file**

Open `jaguarclaw-ui/src/composables/useNodeConsole.ts`.

Find the existing imports line:
```ts
import type { NodeInfo, NodeRegisterPayload, NodeTestResult } from '@/types'
```

Replace with:
```ts
import type { NodeInfo, NodeRegisterPayload, NodeTestResult, NodeImportResult } from '@/types'
import { useToast } from './useToast'
```

- [ ] **Step 2: Add the two new methods inside the useNodeConsole function**

Find the `return {` block at the end. Before it, add:

```ts
  async function importNodes(csvContent: string): Promise<NodeImportResult> {
    error.value = null
    try {
      const result = await request<NodeImportResult>('nodes.import', { csv: csvContent })
      if (result.imported > 0) {
        await loadNodes()
      }
      return result
    } catch (e) {
      console.error('[NodeConsole] Failed to import nodes:', e)
      error.value = e instanceof Error ? e.message : 'Failed to import nodes'
      throw e
    }
  }

  async function downloadTemplate(): Promise<void> {
    const { showToast } = useToast()
    try {
      const result = await request<{ csv: string }>('nodes.import.template')
      const blob = new Blob([result.csv], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'nodes-template.csv'
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error('[NodeConsole] Failed to download template:', e)
      showToast({ type: 'error', message: 'Failed to download template' })
    }
  }
```

- [ ] **Step 3: Export both methods in the return block**

Find the `return {` block. Add `importNodes` and `downloadTemplate` to the returned object:

```ts
  return {
    nodes: readonly(nodes),
    loading: readonly(loading),
    error: readonly(error),
    loadNodes,
    registerNode,
    updateNode,
    removeNode,
    testNode,
    importNodes,
    downloadTemplate
  }
```

- [ ] **Step 4: Build to verify types compile**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run build 2>&1 | tail -15
```

Expected: no TypeScript errors

- [ ] **Step 5: Commit**

```bash
git add jaguarclaw-ui/src/composables/useNodeConsole.ts
git commit -m "feat(nodes): add importNodes and downloadTemplate to useNodeConsole"
```

---

### Task 7: Update i18n (en.ts and zh.ts)

**Files:**
- Modify: `jaguarclaw-ui/src/i18n/locales/en.ts`
- Modify: `jaguarclaw-ui/src/i18n/locales/zh.ts`

- [ ] **Step 1: Update en.ts**

In `en.ts`, in the `nodes` section:

**Remove** the `db` option from `typeOptions`:
```ts
// Remove this line:
db: 'Database',
```

**Update** `emptyHint` (remove database reference):
```ts
// Before:
emptyHint: 'Add SSH, Kubernetes, or Database nodes to enable remote operations.',
// After:
emptyHint: 'Add SSH or Kubernetes nodes to enable remote operations.',
```

**Add** import-related keys to the `nodes` section (add after `errors` block, still inside `nodes: {}`):
```ts
      importBtn: 'Import CSV',
      templateBtn: 'Download Template',
      importingBtn: 'Importing...',
      importSuccess: 'Successfully imported {n} node(s)',
      importSkipped: 'Skipped {n} (duplicate IP or alias): {aliases}',
      importError: 'Row {row} [{field}]: {reason}',
      importErrorNoRow: '{reason}',
      pendingConfig: 'Pending Config',
```

- [ ] **Step 2: Update zh.ts**

In `zh.ts`, in the `nodes` section:

**Remove** the `db` option from `typeOptions`:
```ts
// Remove this line:
db: '数据库',
```

**Update** `emptyHint`:
```ts
// Before:
emptyHint: '添加 SSH、Kubernetes 或数据库节点以启用远程操作。',
// After:
emptyHint: '添加 SSH 或 Kubernetes 节点以启用远程操作。',
```

**Add** import-related keys (same position as en.ts):
```ts
      importBtn: '导入 CSV',
      templateBtn: '下载模板',
      importingBtn: '导入中...',
      importSuccess: '成功导入 {n} 个节点',
      importSkipped: '跳过 {n} 个（IP或别名重复）：{aliases}',
      importError: '第 {row} 行 [{field}]：{reason}',
      importErrorNoRow: '{reason}',
      pendingConfig: '待配置',
```

- [ ] **Step 3: Build to verify no i18n key errors**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run build 2>&1 | tail -10
```

Expected: clean build

- [ ] **Step 4: Commit**

```bash
git add jaguarclaw-ui/src/i18n/locales/en.ts jaguarclaw-ui/src/i18n/locales/zh.ts
git commit -m "feat(nodes): add import i18n keys, remove db connector type strings"
```

---

### Task 8: Update NodesSection.vue (import toolbar, 待配置 badge, DB cleanup)

**Files:**
- Modify: `jaguarclaw-ui/src/components/settings/NodesSection.vue`

This is the largest task. Do it in three logical sub-steps.

#### 8a: DB connector cleanup

- [ ] **Step 1: Remove DB from getTypeBadgeClass**

Find (around line 155):
```ts
function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ssh': return 'badge-ssh'
    case 'k8s': return 'badge-k8s'
    case 'db': return 'badge-db'
    default: return ''
  }
}
```

Replace with:
```ts
function getTypeBadgeClass(type: string): string {
  switch (type) {
    case 'ssh': return 'badge-ssh'
    case 'k8s': return 'badge-k8s'
    default: return ''
  }
}
```

- [ ] **Step 2: Remove DB from getAuthTypeOptions**

Find (around line 178):
```ts
    case 'db': return [
      { value: 'password', label: t('sections.nodes.fields.authOptions.password') },
      { value: 'token', label: t('sections.nodes.fields.authOptions.token') }
    ]
```

Delete the entire `case 'db': return [...]` block.

- [ ] **Step 3: Remove DB from connectorTypeOptions**

Find (around line 188):
```ts
  { label: t('sections.nodes.fields.typeOptions.db'), value: 'db' },
```

Delete that line.

- [ ] **Step 4: Remove .badge-db CSS**

Find in the `<style>` section:
```css
.badge-db {
```

Delete the `.badge-db { ... }` block (typically 3-4 lines).

#### 8b: Import state and logic

- [ ] **Step 5: Add import-related state and composable destructure**

Find the existing destructure at the top of `<script setup>`:
```ts
const { nodes, loading, error, loadNodes, registerNode, updateNode, removeNode, testNode } = useNodeConsole()
```

Replace with:
```ts
const { nodes, loading, error, loadNodes, registerNode, updateNode, removeNode, testNode, importNodes, downloadTemplate } = useNodeConsole()
```

Add the following new reactive state immediately after the existing `const confirmDeleteId = ref<string | null>(null)` line:

```ts
// Import state
const importResult = ref<import('@/types').NodeImportResult | null>(null)
const importing = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
```

- [ ] **Step 6: Add import handler functions**

Add these functions after `handleDelete`:

```ts
async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = '' // reset so same file can be re-selected
  importing.value = true
  importResult.value = null
  try {
    const text = await file.text()
    importResult.value = await importNodes(text)
  } catch {
    // error already set in composable
  } finally {
    importing.value = false
  }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}
```

#### 8c: Template changes

- [ ] **Step 7: Add import toolbar to template**

Find the `<header class="section-header">` block:
```html
<header class="section-header">
  <div class="header-top">
    <div>
      <h2 class="section-title">{{ t('settings.nav.nodes') }}</h2>
      <p class="section-subtitle">{{ t('sections.nodes.subtitle') }}</p>
    </div>
    <button class="add-btn" @click="openForm">{{ t('sections.nodes.addBtn') }}</button>
  </div>
</header>
```

Replace with:
```html
<header class="section-header">
  <div class="header-top">
    <div>
      <h2 class="section-title">{{ t('settings.nav.nodes') }}</h2>
      <p class="section-subtitle">{{ t('sections.nodes.subtitle') }}</p>
    </div>
    <div class="header-actions">
      <button class="secondary-btn" @click="downloadTemplate">{{ t('sections.nodes.templateBtn') }}</button>
      <button class="secondary-btn" :disabled="importing" @click="triggerFileInput">
        {{ importing ? t('sections.nodes.importingBtn') : t('sections.nodes.importBtn') }}
      </button>
      <input ref="fileInputRef" type="file" accept=".csv" style="display:none" @change="handleImportFile" />
      <button class="add-btn" @click="openForm">{{ t('sections.nodes.addBtn') }}</button>
    </div>
  </div>

  <!-- Import result banner -->
  <div v-if="importResult" class="import-banner">
    <p v-if="importResult.imported > 0" class="import-ok">
      {{ t('sections.nodes.importSuccess', { n: String(importResult.imported) }) }}
    </p>
    <p v-if="importResult.skipped > 0" class="import-warn">
      {{ t('sections.nodes.importSkipped', {
        n: String(importResult.skipped),
        aliases: importResult.skippedAliases.join(', ')
      }) }}
    </p>
    <p v-for="(err, i) in importResult.errors" :key="i" class="import-err">
      <template v-if="err.row > 0">
        {{ t('sections.nodes.importError', { row: String(err.row), field: err.field ?? '-', reason: err.reason }) }}
      </template>
      <template v-else>
        {{ t('sections.nodes.importErrorNoRow', { reason: err.reason }) }}
      </template>
    </p>
  </div>
</header>
```

- [ ] **Step 8: Add 待配置 badge to node cards**

In `NodesSection.vue`, find the exact block at lines ~347-348:

```html
            <span class="node-alias">{{ node.alias }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(node.connectorType)">
```

Replace with (badge inserted between the two spans):

```html
            <span class="node-alias">{{ node.alias }}</span>
            <span v-if="node.authType === null" class="badge-pending">{{ t('sections.nodes.pendingConfig') }}</span>
            <span class="type-badge" :class="getTypeBadgeClass(node.connectorType)">
```

- [ ] **Step 9: Add CSS for new elements**

In the `<style scoped>` section, add:

```css
/* Header actions row */
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

/* Secondary action button */
.secondary-btn {
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-gray-300);
  background: var(--color-gray-50);
  color: var(--color-gray-700);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}
.secondary-btn:hover:not(:disabled) { background: var(--color-gray-100); }
.secondary-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* Import result banner */
.import-banner {
  margin-top: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-gray-50);
  border: 1px solid var(--color-gray-200);
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.import-ok  { color: #16a34a; }
.import-warn { color: #d97706; }
.import-err { color: #dc2626; }

/* Pending config badge */
.badge-pending {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: #fed7aa;
  color: #c2410c;
  font-size: 10px;
  font-weight: 600;
  margin-left: var(--space-1);
}
```

- [ ] **Step 10: Build to verify no TypeScript or template errors**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run build 2>&1 | tail -15
```

Expected: clean build

- [ ] **Step 11: Commit**

```bash
git add jaguarclaw-ui/src/components/settings/NodesSection.vue
git commit -m "feat(nodes): add CSV import toolbar, pending-config badge, remove db connector UI"
```

---

## Final verification

- [ ] **Run full backend test suite**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw
mvn test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Run frontend build**

```bash
cd /Users/eumenides/Desktop/jaguarliu/core/miniclaw/jaguarclaw-ui
npm run build 2>&1 | tail -5
```

Expected: `✓ built in`

- [ ] **Manual smoke test**
  1. Start backend + frontend in dev mode
  2. Go to Settings → Nodes
  3. Click "Download Template" — verify `nodes-template.csv` downloads with correct headers and two example rows
  4. Fill the CSV with valid SSH entries (use a real IP on your LAN if possible, or fictional ones)
  5. Click "Import CSV" → select file → verify success banner shows correct counts
  6. Verify imported nodes appear in the list with orange "待配置" badge
  7. Click edit on an imported node → verify form opens with `authType = password`, credential field empty
  8. Add credential, save — verify "待配置" badge disappears
  9. Try importing the same CSV again — verify all rows show as skipped (IP/alias dedup)
  10. Import a CSV with intentional errors — verify row-level error messages appear in banner
  11. Verify "Database" option is gone from the Type dropdown in the Add Node form
