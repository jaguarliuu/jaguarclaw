package com.jaguarliu.ai.im.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Broadcasts IM events to all active WebSocket connections.
 * Wraps ConnectionManager to provide a typed publish API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImEventPublisher {

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    /**
     * Serializes the payload and broadcasts to all active WebSocket connections.
     * A failure on one connection does not prevent delivery to others.
     *
     * @param eventType the event name (e.g. "im.message.new")
     * @param payload   the event payload object (will be serialized to JSON)
     */
    public void broadcast(String eventType, Object payload) {
        String json;
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", "event");
            envelope.put("event", eventType);
            envelope.put("payload", payload);
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("[IM] Failed to serialize event payload for eventType={}", eventType, e);
            return;
        }

        for (String connectionId : connectionManager.getAllConnectionIds()) {
            try {
                connectionManager.emit(connectionId, json);
            } catch (Exception e) {
                log.warn("[IM] Failed to emit event to connectionId={}", connectionId, e);
            }
        }
    }
}
