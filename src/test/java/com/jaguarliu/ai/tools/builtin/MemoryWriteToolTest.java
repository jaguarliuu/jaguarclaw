package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.model.MemoryScope;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryWriteTool 单元测试
 */
@DisplayName("MemoryWriteTool Tests")
@ExtendWith(MockitoExtension.class)
class MemoryWriteToolTest {

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private MemoryIndexer memoryIndexer;

    @InjectMocks
    private MemoryWriteTool tool;

    // ==================== getDefinition 测试 ====================

    @Nested
    @DisplayName("getDefinition")
    class GetDefinitionTests {

        @Test
        @DisplayName("返回正确的工具名称")
        void returnsCorrectName() {
            ToolDefinition def = tool.getDefinition();
            assertEquals("memory_write", def.getName());
        }

        @Test
        @DisplayName("不需要 HITL 确认")
        void doesNotRequireHitl() {
            assertFalse(tool.requiresHitl());
        }

        @Test
        @DisplayName("包含 file（可选）、content（必须）和 scope 参数定义")
        void hasRequiredParameters() {
            ToolDefinition def = tool.getDefinition();
            Map<String, Object> params = def.getParameters();

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) params.get("properties");
            assertTrue(properties.containsKey("file"));
            assertTrue(properties.containsKey("content"));
            assertTrue(properties.containsKey("scope"));

            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) params.get("required");
            assertTrue(required.contains("content"));
            assertFalse(required.contains("file"), "file should be optional");
        }

        @Test
        @DisplayName("scope 参数有 enum 限制")
        void scopeHasEnumConstraint() {
            ToolDefinition def = tool.getDefinition();

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) def.getParameters().get("properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> scopeDef = (Map<String, Object>) properties.get("scope");
            @SuppressWarnings("unchecked")
            List<String> scopeEnum = (List<String>) scopeDef.get("enum");
            assertTrue(scopeEnum.contains("agent"));
            assertTrue(scopeEnum.contains("global"));
            assertEquals(2, scopeEnum.size());
        }
    }

    // ==================== execute 测试 ====================

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @AfterEach
        void tearDown() {
            ToolExecutionContext.clear();
        }

        @Test
        @DisplayName("content 为 null 时返回错误")
        void nullContentReturnsError() {
            ToolResult result = tool.execute(Map.of()).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: content"));
        }

        @Test
        @DisplayName("content 为空白时返回错误")
        void blankContentReturnsError() {
            ToolResult result = tool.execute(Map.of("content", "   ")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Missing required parameter: content"));
        }

        @Test
        @DisplayName("file 省略时默认写入今日日期文件")
        void fileOmittedWritesToDaily() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            ToolResult result = tool.execute(Map.of("content", "Today's note")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToDaily("Today's note", "agent-a", MemoryScope.AGENT);

            String todayFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            verify(memoryIndexer).indexFile(todayFileName, MemoryScope.AGENT, "agent-a");
            assertTrue(result.getContent().contains(todayFileName));
        }

        @Test
        @DisplayName("file 为空字符串时也写入今日日期文件")
        void blankFileWritesToDaily() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            ToolResult result = tool.execute(Map.of("content", "Test", "file", "  ")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToDaily("Test", "agent-a", MemoryScope.AGENT);
        }

        @Test
        @DisplayName("file=\"projects.md\" 调用 appendToMemoryFile")
        void fileSpecifiedWritesToMemoryFile() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            ToolResult result = tool.execute(Map.of(
                    "content", "Project notes",
                    "file", "projects.md"
            )).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToMemoryFile("projects.md", "Project notes", "agent-a", MemoryScope.AGENT);
            verify(memoryIndexer).indexFile("projects.md", MemoryScope.AGENT, "agent-a");
            assertTrue(result.getContent().contains("projects.md"));
        }

        @Test
        @DisplayName("file=\"MEMORY.md\" 合法写入索引文件")
        void fileEqualsMemoryMdIsValid() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            ToolResult result = tool.execute(Map.of(
                    "content", "## Memory Files\n- notes.md",
                    "file", "MEMORY.md"
            )).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToMemoryFile("MEMORY.md", "## Memory Files\n- notes.md", "agent-a", MemoryScope.AGENT);
            verify(memoryIndexer).indexFile("MEMORY.md", MemoryScope.AGENT, "agent-a");
        }

        @Test
        @DisplayName("scope=global 写入全局记忆")
        void globalScopeWritesToGlobalMemory() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            ToolResult result = tool.execute(Map.of(
                    "content", "Shared info",
                    "file", "notes.md",
                    "scope", "global"
            )).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToMemoryFile("notes.md", "Shared info", "agent-a", MemoryScope.GLOBAL);
            verify(memoryIndexer).indexFile("notes.md", MemoryScope.GLOBAL, "agent-a");
        }

        @Test
        @DisplayName("scope 省略时默认 agent 私有")
        void scopeDefaultsToAgent() throws IOException {
            ToolExecutionContext.set(ToolExecutionContext.builder().agentId("agent-a").build());
            tool.execute(Map.of("content", "test", "file", "notes.md")).block();

            verify(memoryStore).appendToMemoryFile("notes.md", "test", "agent-a", MemoryScope.AGENT);
        }

        @Test
        @DisplayName("scope 非法时返回错误")
        void invalidScopeReturnsError() {
            ToolResult result = tool.execute(Map.of(
                    "content", "abc",
                    "scope", "both"
            )).block();
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Invalid scope"));
        }

        @Test
        @DisplayName("返回内容包含字符数")
        void resultIncludesCharCount() throws IOException {
            String content = "Hello World";
            ToolResult result = tool.execute(Map.of("content", content)).block();

            assertNotNull(result);
            assertTrue(result.getContent().contains("11 chars"));
        }

        @Test
        @DisplayName("写入失败时返回错误")
        void writeFailureReturnsError() throws IOException {
            doThrow(new IOException("Disk full")).when(memoryStore)
                    .appendToDaily(anyString(), anyString(), any(MemoryScope.class));

            ToolResult result = tool.execute(Map.of("content", "test")).block();

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getContent().contains("Memory write failed"));
            assertTrue(result.getContent().contains("Disk full"));
        }

        @Test
        @DisplayName("索引失败不影响写入成功")
        void indexFailureDoesNotAffectWrite() throws IOException {
            doThrow(new RuntimeException("Index error")).when(memoryIndexer)
                    .indexFile(anyString(), any(MemoryScope.class), anyString());

            ToolResult result = tool.execute(Map.of("content", "test")).block();

            assertNotNull(result);
            assertTrue(result.isSuccess()); // 写入仍然成功
            verify(memoryStore).appendToDaily("test", "main", MemoryScope.AGENT);
        }

        @Test
        @DisplayName("写入大量内容")
        void writeLargeContent() throws IOException {
            String largeContent = "x".repeat(10000);
            ToolResult result = tool.execute(Map.of("content", largeContent)).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            verify(memoryStore).appendToDaily(largeContent, "main", MemoryScope.AGENT);
            assertTrue(result.getContent().contains("10000 chars"));
        }
    }

    // ==================== Tool 接口便捷方法测试 ====================

    @Nested
    @DisplayName("Tool interface methods")
    class ToolInterfaceMethodsTests {

        @Test
        @DisplayName("getName 返回正确名称")
        void getNameReturnsCorrectName() {
            assertEquals("memory_write", tool.getName());
        }

        @Test
        @DisplayName("requiresHitl 返回 false")
        void requiresHitlReturnsFalse() {
            assertFalse(tool.requiresHitl());
        }
    }
}
