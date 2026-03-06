package com.jaguarliu.ai.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Logback config tests")
class LogbackConfigTest {

    @Test
    @DisplayName("logback-spring should define split rolling log files")
    void logbackSpringShouldDefineSplitRollingFiles() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            assertNotNull(input, "logback-spring.xml should exist on classpath");
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("app.log"));
            assertTrue(xml.contains("rpc.log"));
            assertTrue(xml.contains("runtime.log"));
            assertTrue(xml.contains("SizeAndTimeBasedRollingPolicy"));
            assertTrue(xml.contains("10MB"));
            assertTrue(xml.contains("<maxHistory>7</maxHistory>"));
            assertTrue(xml.contains("200MB"));
        }
    }
}
