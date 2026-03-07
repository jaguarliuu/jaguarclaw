package com.jaguarliu.ai.gateway.events;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("EventBus Tests")
class EventBusTest {

    @Test
    @DisplayName("should not log high-frequency stream events")
    void shouldNotLogHighFrequencyStreamEvents() {
        EventBus eventBus = new EventBus(mock(ConnectionManager.class), new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(EventBus.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            eventBus.publish(AgentEvent.assistantDelta("__scheduled__", "run-1", "hello"));
            eventBus.publish(AgentEvent.artifactDelta("__scheduled__", "run-1", "content"));

            assertTrue(appender.list.isEmpty(), "stream events should not be logged");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("should log key agent actions")
    void shouldLogKeyAgentActions() {
        EventBus eventBus = new EventBus(mock(ConnectionManager.class), new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(EventBus.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            eventBus.publish(AgentEvent.toolCall("__scheduled__", "run-1", "call-1", "write_file", "{}"));

            assertFalse(appender.list.isEmpty(), "tool call should be logged");
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("tool.call")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
