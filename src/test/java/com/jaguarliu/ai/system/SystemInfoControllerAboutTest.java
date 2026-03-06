package com.jaguarliu.ai.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SystemInfoController About tests")
class SystemInfoControllerAboutTest {

    @Test
    @DisplayName("should return local static changelog entries")
    void shouldReturnLocalStaticChangelogEntries() {
        SystemInfoService systemInfoService = mock(SystemInfoService.class);
        SystemInfoController controller = new SystemInfoController(systemInfoService);

        Map<String, Object> response = controller.getChangelog();

        assertTrue(response.containsKey("entries"));
        Object entries = response.get("entries");
        assertInstanceOf(List.class, entries);
        assertFalse(((List<?>) entries).isEmpty());

        Object first = ((List<?>) entries).get(0);
        assertInstanceOf(Map.class, first);
        Map<?, ?> firstEntry = (Map<?, ?>) first;
        assertTrue(firstEntry.containsKey("version"));
        assertTrue(firstEntry.containsKey("date"));
        assertTrue(firstEntry.containsKey("sections"));
        assertInstanceOf(Map.class, firstEntry.get("sections"));
    }

    @Test
    @DisplayName("should keep existing system info endpoint working")
    void shouldKeepSystemInfoEndpointWorking() {
        SystemInfoService.SystemInfo info = SystemInfoService.SystemInfo.builder()
                .os("Windows")
                .osVersion("11")
                .architecture("amd64")
                .javaVersion("24")
                .javaVendor("Temurin")
                .userHome("C:/Users/test")
                .userName("tester")
                .totalMemory(1L)
                .freeMemory(1L)
                .maxMemory(1L)
                .availableProcessors(8)
                .build();

        SystemInfoService systemInfoService = mock(SystemInfoService.class);
        when(systemInfoService.getSystemInfo()).thenReturn(info);

        SystemInfoController controller = new SystemInfoController(systemInfoService);
        Map<String, Object> response = controller.getSystemInfo();

        assertEquals("Windows", response.get("os"));
        assertEquals("tester", response.get("userName"));
        assertEquals(8, response.get("availableProcessors"));
    }
}
