package com.jaguarliu.ai.memory.model;

/**
 * Memory 检索/写入作用域
 */
public enum MemoryScope {
    GLOBAL,
    AGENT,
    BOTH;

    public boolean includesGlobal() {
        return this == GLOBAL || this == BOTH;
    }

    public boolean includesAgent() {
        return this == AGENT || this == BOTH;
    }

    public static MemoryScope from(String value, MemoryScope defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return MemoryScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
