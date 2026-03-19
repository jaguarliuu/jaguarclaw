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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        lenient().when(nodeRepository.existsByHost(any())).thenReturn(false);
        lenient().when(nodeRepository.existsByAlias(any())).thenReturn(false);
    }

    private RpcResponse call(String csv) {
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of("csv", csv));
        RpcRequest req = RpcRequest.builder()
                .id("r1")
                .method("nodes.import")
                .payload(Map.of("csv", csv))
                .build();
        return handler.handle("c1", req).block();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(RpcResponse response) {
        return (Map<String, Object>) response.getPayload();
    }

    @Test
    @DisplayName("imports valid rows and returns count")
    void importsValidRows() {
        RpcResponse response = call(HEADER + "\n" + VALID_ROW);
        assertNull(response.getError());

        Map<String, Object> payload = payload(response);
        assertEquals(1, payload.get("imported"));
        assertEquals(0, payload.get("skipped"));
        assertTrue(((List<?>) payload.get("errors")).isEmpty());

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
        assertEquals("prod,web", saved.getTags());
        assertEquals("standard", saved.getSafetyPolicy());
    }

    @Test
    @DisplayName("defaults safetyPolicy to standard when blank")
    void defaultsSafetyPolicy() {
        call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,");

        ArgumentCaptor<List<NodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(nodeRepository).saveAll(captor.capture());
        assertEquals("standard", captor.getValue().get(0).getSafetyPolicy());
    }

    @Test
    @DisplayName("stores null tags when tags column is empty")
    void nullTagsWhenEmpty() {
        call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");

        ArgumentCaptor<List<NodeEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(nodeRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getTags());
    }

    @Test
    @DisplayName("header is case-insensitive and trimmed")
    void caseInsensitiveHeader() {
        String weirdHeader = "Alias , DisplayName , Host , Port , Username , Tags , SafetyPolicy";
        RpcResponse response = call(weirdHeader + "\n" + VALID_ROW);
        assertNull(response.getError());
        assertEquals(1, payload(response).get("imported"));
    }

    @Test
    @DisplayName("returns row:0 error for empty CSV")
    void emptyCsv() {
        RpcResponse response = call("   ");
        assertNull(response.getError());
        List<?> errors = (List<?>) payload(response).get("errors");
        assertFalse(errors.isEmpty());
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
    }

    @Test
    @DisplayName("returns row:0 error for wrong header")
    void wrongHeader() {
        RpcResponse response = call("wrong,header,cols\nweb-01,x,192.168.1.10,22,deploy,,standard");
        assertNull(response.getError());
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals(1, errors.size());
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("returns row:0 error when exceeding 500 rows")
    void tooManyRows() {
        StringBuilder builder = new StringBuilder(HEADER);
        for (int i = 0; i < 501; i++) {
            builder.append("\nweb-").append(i)
                    .append(",,192.168.1.").append(i % 256)
                    .append(",22,deploy,,standard");
        }

        RpcResponse response = call(builder.toString());
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals(0, ((Map<?, ?>) errors.get(0)).get("row"));
        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("error on wrong column count")
    void wrongColumnCount() {
        RpcResponse response = call(HEADER + "\nweb-01,only-three-cols");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals(1, errors.size());
        Map<?, ?> error = (Map<?, ?>) errors.get(0);
        assertEquals(2, error.get("row"));
        assertTrue(error.get("reason").toString().contains("Expected 7 columns"));
    }

    @Test
    @DisplayName("error when alias is blank")
    void missingAlias() {
        RpcResponse response = call(HEADER + "\n,Display,192.168.1.10,22,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("alias", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when host is blank")
    void missingHost() {
        RpcResponse response = call(HEADER + "\nweb-01,Display,,22,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "host".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when host is localhost")
    void localhostHost() {
        RpcResponse response = call(HEADER + "\nweb-01,,localhost,22,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("host", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when host is 127.0.0.1")
    void loopbackHost() {
        RpcResponse response = call(HEADER + "\nweb-01,,127.0.0.1,22,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("host", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when port is blank")
    void missingPort() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "port".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when port is out of range")
    void portOutOfRange() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,99999,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("port", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when port is not a number")
    void portNotNumber() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,abc,deploy,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("port", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when username is blank")
    void missingUsername() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,22,,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertTrue(errors.stream().anyMatch(e -> "username".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("error when username is root")
    void rootUsername() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,22,root,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("username", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("error when safetyPolicy is invalid")
    void invalidSafetyPolicy() {
        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,extreme");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals("safetyPolicy", ((Map<?, ?>) errors.get(0)).get("field"));
    }

    @Test
    @DisplayName("collects all errors for a row without stopping early")
    void collectsAllRowErrors() {
        RpcResponse response = call(HEADER + "\nweb-01,,,22,root,,standard");
        List<?> errors = (List<?>) payload(response).get("errors");
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> "host".equals(((Map<?, ?>) e).get("field"))));
        assertTrue(errors.stream().anyMatch(e -> "username".equals(((Map<?, ?>) e).get("field"))));
    }

    @Test
    @DisplayName("partial success: valid rows imported even when other rows error")
    void partialSuccess() {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"
                + "\n,bad,,22,root,,standard";

        RpcResponse response = call(csv);
        Map<String, Object> payload = payload(response);
        assertEquals(1, payload.get("imported"));
        assertFalse(((List<?>) payload.get("errors")).isEmpty());
    }

    @Test
    @DisplayName("within-CSV host dedup: second row with same host is skipped")
    void withinCsvHostDedup() {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"
                + "\nweb-02,,192.168.1.10,22,deploy,,standard";

        RpcResponse response = call(csv);
        Map<String, Object> payload = payload(response);
        assertEquals(1, payload.get("imported"));
        assertEquals(1, payload.get("skipped"));
        assertTrue(((List<?>) payload.get("skippedAliases")).contains("web-02"));
    }

    @Test
    @DisplayName("within-CSV alias dedup: second row with same alias is skipped")
    void withinCsvAliasDedup() {
        String csv = HEADER
                + "\nweb-01,,192.168.1.10,22,deploy,,standard"
                + "\nweb-01,,192.168.1.11,22,deploy,,standard";

        RpcResponse response = call(csv);
        Map<String, Object> payload = payload(response);
        assertEquals(1, payload.get("imported"));
        assertEquals(1, payload.get("skipped"));
        assertTrue(((List<?>) payload.get("skippedAliases")).contains("web-01"));
    }

    @Test
    @DisplayName("DB host dedup: row skipped when host already in DB")
    void dbHostDedup() {
        when(nodeRepository.existsByHost("192.168.1.10")).thenReturn(true);

        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");
        Map<String, Object> payload = payload(response);
        assertEquals(0, payload.get("imported"));
        assertEquals(1, payload.get("skipped"));
        assertTrue(((List<?>) payload.get("skippedAliases")).contains("web-01"));
        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("DB alias dedup: row skipped when alias already in DB")
    void dbAliasDedup() {
        when(nodeRepository.existsByAlias("web-01")).thenReturn(true);

        RpcResponse response = call(HEADER + "\nweb-01,,192.168.1.10,22,deploy,,standard");
        Map<String, Object> payload = payload(response);
        assertEquals(0, payload.get("imported"));
        assertEquals(1, payload.get("skipped"));
    }
}
