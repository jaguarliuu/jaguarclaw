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
import reactor.core.Disposable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("EventBus Tests")
class EventBusTest {

    @Test
    @DisplayName("should ignore zero subscriber sink result for stream events")
    void shouldIgnoreZeroSubscriberSinkResultForStreamEvents() {
        EventBus eventBus = new EventBus(mock(ConnectionManager.class), new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(EventBus.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        Disposable subscription = eventBus.subscribeAll().subscribe();
        subscription.dispose();

        try {
            eventBus.publish(AgentEvent.assistantDelta("__scheduled__", "run-1", "hello"));
            eventBus.publish(AgentEvent.tokenUsage("__scheduled__", "run-1",
                    com.jaguarliu.ai.llm.model.LlmResponse.Usage.builder()
                            .promptTokens(1).completionTokens(1).totalTokens(2).build(), 0, 1));

            assertTrue(appender.list.isEmpty(), "zero-subscriber stream events should stay silent");
        } finally {
            logger.detachAppender(appender);
        }
    }


    @Test
    @DisplayName("should tolerate concurrent publishes")
    void shouldTolerateConcurrentPublishes() throws Exception {
        EventBus eventBus = new EventBus(mock(ConnectionManager.class), new ObjectMapper());
        eventBus.subscribeAll().subscribe();

        int threads = 8;
        int publishesPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int threadNo = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            failures.add(new AssertionError("start latch timeout"));
                            return;
                        }
                        for (int j = 0; j < publishesPerThread; j++) {
                            eventBus.publish(AgentEvent.assistantDelta("__scheduled__", "run-" + threadNo, "chunk-" + j));
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers should be ready");
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "workers should finish");
            assertTrue(failures.isEmpty(), () -> "unexpected failures: " + failures);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "executor should terminate");
        }
    }

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
