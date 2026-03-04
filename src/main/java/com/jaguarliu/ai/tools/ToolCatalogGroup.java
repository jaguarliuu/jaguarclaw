package com.jaguarliu.ai.tools;

import java.util.List;

/**
 * 统一工具目录分组
 */
public record ToolCatalogGroup(
        String category,
        String label,
        int order,
        List<ToolCatalogEntry> tools
) {
}

