package com.jaguarliu.ai.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统信息和环境检测接口
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> CHANGELOG_TYPE = new TypeReference<>() {};

    private final SystemInfoService systemInfoService;

    /**
     * 获取系统信息
     */
    @GetMapping("/info")
    public Map<String, Object> getSystemInfo() {
        SystemInfoService.SystemInfo info = systemInfoService.getSystemInfo();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("os", info.getOs());
        response.put("osVersion", info.getOsVersion());
        response.put("architecture", info.getArchitecture());
        response.put("javaVersion", info.getJavaVersion());
        response.put("javaVendor", info.getJavaVendor());
        response.put("userHome", info.getUserHome());
        response.put("userName", info.getUserName());
        response.put("totalMemory", info.getTotalMemory());
        response.put("freeMemory", info.getFreeMemory());
        response.put("maxMemory", info.getMaxMemory());
        response.put("availableProcessors", info.getAvailableProcessors());

        return response;
    }

    /**
     * 检测环境
     */
    @GetMapping("/environment")
    public Map<String, Object> checkEnvironment() {
        List<SystemInfoService.EnvironmentCheck> checks = systemInfoService.checkEnvironments();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("environments", checks);

        return response;
    }

    /**
     * 获取本地静态更新日志
     */
    @GetMapping("/changelog")
    public Map<String, Object> getChangelog() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("entries", loadChangelogEntries());
        return response;
    }

    private List<Map<String, Object>> loadChangelogEntries() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("changelog.json")) {
            if (input == null) {
                log.warn("changelog.json not found on classpath");
                return List.of();
            }
            List<Map<String, Object>> entries = OBJECT_MAPPER.readValue(input, CHANGELOG_TYPE);
            return normalizeChangelogEntries(entries);
        } catch (Exception e) {
            log.warn("Failed to load changelog.json", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> normalizeChangelogEntries(List<Map<String, Object>> entries) {
        return entries.stream().map(entry -> {
            Map<String, Object> normalized = new LinkedHashMap<>(entry);
            Object sectionsObject = normalized.get("sections");
            if (sectionsObject instanceof Map<?, ?> sectionsMap) {
                normalized.put("sections", normalizeSectionsMap(sectionsMap));
                return normalized;
            }

            Map<String, Object> sections = new LinkedHashMap<>();
            sections.put("added", List.of());
            sections.put("changed", normalizeStringList(normalized.get("items")));
            sections.put("fixed", List.of());
            normalized.put("sections", sections);
            return normalized;
        }).toList();
    }

    private Map<String, Object> normalizeSectionsMap(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("added", normalizeStringList(raw.get("added")));
        normalized.put("changed", normalizeStringList(raw.get("changed")));
        normalized.put("fixed", normalizeStringList(raw.get("fixed")));
        return normalized;
    }

    private List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
