package com.jaguarliu.ai.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ToolRegistry 渐进式加载测试
 */
class ToolRegistryProgressiveTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();

        // 注册测试工具
        registry.register(new MockTool("read_file", "Read file contents", "low"));
        registry.register(new MockTool("write_file", "Write file contents", "medium"));
        registry.register(new MockTool("shell", "Execute shell command", "high"));
    }

    @Test
    @DisplayName("Progressive loading with empty L1 set should return all L0")
    void testAllL0() {
        List<Map<String, Object>> tools = registry.toOpenAiToolsProgressive(Set.of());

        assertEquals(3, tools.size());

        // 所有工具都是 L0 格式（无 parameters）
        for (Map<String, Object> tool : tools) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            assertNull(function.get("parameters"), "L0 should not have parameters");
        }
    }

    @Test
    @DisplayName("Progressive loading with L1 set should upgrade specified tools")
    void testMixedL0L1() {
        Set<String> l1ToolIds = Set.of("read_file");

        List<Map<String, Object>> tools = registry.toOpenAiToolsProgressive(l1ToolIds);

        assertEquals(3, tools.size());

        // read_file 应该是 L1
        Map<String, Object> readFile = tools.stream()
                .filter(t -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> f = (Map<String, Object>) t.get("function");
                    return "read_file".equals(f.get("name"));
                })
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> readFunction = (Map<String, Object>) readFile.get("function");

        // L1 应该有 parameters（即使是空的）
        assertNotNull(readFunction.get("parameters"));
    }

    @Test
    @DisplayName("Token estimation should be accurate")
    void testTokenEstimation() {
        // 全部 L0
        int l0Only = registry.estimateProgressiveTokens(Set.of());
        
        // 部分升级到 L1
        int withL1 = registry.estimateProgressiveTokens(Set.of("read_file", "write_file"));

        assertTrue(l0Only > 0, "L0 tokens should be positive");
        assertTrue(withL1 > l0Only, "With L1 should have more tokens");
        
        // 估算节省
        int saved = withL1 - l0Only;
        assertTrue(saved < 500, "Token difference should be reasonable");
    }

    @Test
    @DisplayName("L0 total should be less than full injection")
    void testL0VsFull() {
        int l0Total = registry.estimateL0Tokens();
        List<Map<String, Object>> fullTools = registry.toOpenAiTools();

        // 全量注入估算（每个工具约 200-500 tokens）
        int fullEstimate = fullTools.size() * 300;

        assertTrue(l0Total < fullEstimate, 
                "L0 should be less than full injection");
    }

    // Mock Tool for testing
    private static class MockTool implements Tool {
        private final String name;
        private final String description;
        private final String riskLevel;

        MockTool(String name, String description, String riskLevel) {
            this.name = name;
            this.description = description;
            this.riskLevel = riskLevel;
        }

        @Override
        public ToolDefinition getDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description(description)
                    .parameters(Map.of("type", "object", "properties", Map.of()))
                    .tags(List.of("test"))
                    .riskLevel(riskLevel)
                    .parameterSummary("Test parameter summary")
                    .example(name + "({})")
                    .build();
        }

        @Override
        public reactor.core.publisher.Mono<ToolResult> execute(Map<String, Object> arguments) {
            return reactor.core.publisher.Mono.just(ToolResult.success("Mock result"));
        }
    }
}
