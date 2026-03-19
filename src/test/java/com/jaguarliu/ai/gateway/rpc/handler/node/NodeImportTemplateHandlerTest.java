package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeImportTemplateHandlerTest {

    private final NodeImportTemplateHandler handler = new NodeImportTemplateHandler();

    @Test
    void returnsCorrectMethod() {
        assertEquals("nodes.import.template", handler.getMethod());
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsCsvWithExpectedHeader() {
        RpcRequest request = RpcRequest.builder()
                .id("r1")
                .method("nodes.import.template")
                .payload(null)
                .build();

        RpcResponse response = handler.handle("c1", request).block();
        assertNotNull(response);
        assertNull(response.getError());
        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        String csv = (String) payload.get("csv");
        assertNotNull(csv);
        assertTrue(csv.startsWith("alias,displayName,host,port,username,tags,safetyPolicy"));
    }
}
