package com.jaguarliu.ai.memory.index;

import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryChunkEntity 单元测试
 *
 * 测试覆盖：
 * 1. Builder 创建
 * 2. 字段访问
 * 3. @PrePersist / @PreUpdate 回调
 * 4. 边界值
 */
@DisplayName("MemoryChunkEntity Tests")
class MemoryChunkEntityTest {

    // ==================== Builder 测试 ====================

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("创建完整实体")
        void createFullEntity() {
            LocalDateTime now = LocalDateTime.now();

            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .id("chunk-001")
                    .filePath("MEMORY.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("Test content")
                    .scope(MemoryChunkEntity.SCOPE_AGENT)
                    .agentId("writer")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertEquals("chunk-001", entity.getId());
            assertEquals("MEMORY.md", entity.getFilePath());
            assertEquals(1, entity.getLineStart());
            assertEquals(10, entity.getLineEnd());
            assertEquals("Test content", entity.getContent());
            assertEquals(MemoryChunkEntity.SCOPE_AGENT, entity.getScope());
            assertEquals("writer", entity.getAgentId());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now, entity.getUpdatedAt());
        }

        @Test
        @DisplayName("Builder 默认值")
        void builderDefaults() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder().build();

            assertNull(entity.getId());
            assertNull(entity.getFilePath());
            assertEquals(0, entity.getLineStart());
            assertEquals(0, entity.getLineEnd());
            assertNull(entity.getContent());
            assertEquals(MemoryChunkEntity.SCOPE_GLOBAL, entity.getScope());
            assertNull(entity.getAgentId());
        }
    }

    // ==================== Setter 测试 ====================

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("所有 setter 工作正常")
        void allSettersWork() {
            MemoryChunkEntity entity = new MemoryChunkEntity();

            entity.setId("id-123");
            entity.setFilePath("2026-01-15.md");
            entity.setLineStart(5);
            entity.setLineEnd(15);
            entity.setContent("Updated content");
            entity.setScope(MemoryChunkEntity.SCOPE_AGENT);
            entity.setAgentId("main");

            assertEquals("id-123", entity.getId());
            assertEquals("2026-01-15.md", entity.getFilePath());
            assertEquals(5, entity.getLineStart());
            assertEquals(15, entity.getLineEnd());
            assertEquals("Updated content", entity.getContent());
            assertEquals(MemoryChunkEntity.SCOPE_AGENT, entity.getScope());
            assertEquals("main", entity.getAgentId());
        }
    }

    // ==================== @PrePersist / @PreUpdate 测试 ====================

    @Nested
    @DisplayName("Lifecycle callbacks")
    class LifecycleCallbackTests {

        @Test
        @DisplayName("onCreate 设置 createdAt 和 updatedAt")
        void onCreateSetsTimestamps() {
            MemoryChunkEntity entity = new MemoryChunkEntity();
            entity.setScope(null);
            assertNull(entity.getCreatedAt());
            assertNull(entity.getUpdatedAt());

            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
            assertEquals(MemoryChunkEntity.SCOPE_GLOBAL, entity.getScope());
            // createdAt 和 updatedAt 应该相等或非常接近
            assertEquals(entity.getCreatedAt().toLocalDate(), entity.getUpdatedAt().toLocalDate());
        }

        @Test
        @DisplayName("onUpdate 只更新 updatedAt")
        void onUpdateOnlyUpdatesUpdatedAt() throws InterruptedException {
            MemoryChunkEntity entity = new MemoryChunkEntity();
            entity.onCreate();

            LocalDateTime originalCreatedAt = entity.getCreatedAt();
            LocalDateTime originalUpdatedAt = entity.getUpdatedAt();

            // 等待一小段时间确保时间戳不同
            Thread.sleep(10);

            entity.onUpdate();

            assertEquals(originalCreatedAt, entity.getCreatedAt()); // createdAt 不变
            assertTrue(entity.getUpdatedAt().isAfter(originalUpdatedAt) ||
                       entity.getUpdatedAt().equals(originalUpdatedAt)); // updatedAt 更新
        }
    }

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("Boundary values")
    class BoundaryValueTests {

        @Test
        @DisplayName("lineStart 和 lineEnd 相同（单行 chunk）")
        void singleLineChunk() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .lineStart(5)
                    .lineEnd(5)
                    .build();

            assertEquals(5, entity.getLineStart());
            assertEquals(5, entity.getLineEnd());
        }

        @Test
        @DisplayName("空内容")
        void emptyContent() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .content("")
                    .build();

            assertEquals("", entity.getContent());
        }

        @Test
        @DisplayName("大内容")
        void largeContent() {
            String largeContent = "x".repeat(100000);
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .content(largeContent)
                    .build();

            assertEquals(100000, entity.getContent().length());
        }

        @Test
        @DisplayName("包含特殊字符的文件路径")
        void specialCharactersInFilePath() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .filePath("memory/2026-01-15/日记.md")
                    .build();

            assertEquals("memory/2026-01-15/日记.md", entity.getFilePath());
        }

        @Test
        @DisplayName("包含中文和 emoji 的内容")
        void chineseAndEmojiContent() {
            String content = "今天学习了 Memory 系统 🎉\n## 关键点\n- 全局记忆";
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .content(content)
                    .build();

            assertTrue(entity.getContent().contains("🎉"));
            assertTrue(entity.getContent().contains("全局记忆"));
        }

        @Test
        @DisplayName("lineStart 为 1（最小有效值）")
        void lineStartMinimum() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .lineStart(1)
                    .build();

            assertEquals(1, entity.getLineStart());
        }

        @Test
        @DisplayName("lineEnd 为 Integer.MAX_VALUE")
        void lineEndMaxValue() {
            MemoryChunkEntity entity = MemoryChunkEntity.builder()
                    .lineEnd(Integer.MAX_VALUE)
                    .build();

            assertEquals(Integer.MAX_VALUE, entity.getLineEnd());
        }
    }

    // ==================== equals/hashCode 测试 ====================

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同字段的实体相等")
        void equalEntities() {
            LocalDateTime now = LocalDateTime.now();

            MemoryChunkEntity e1 = MemoryChunkEntity.builder()
                    .id("id-1")
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("content")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            MemoryChunkEntity e2 = MemoryChunkEntity.builder()
                    .id("id-1")
                    .filePath("test.md")
                    .lineStart(1)
                    .lineEnd(10)
                    .content("content")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("不同 ID 的实体不相等")
        void differentIdNotEqual() {
            MemoryChunkEntity e1 = MemoryChunkEntity.builder().id("id-1").build();
            MemoryChunkEntity e2 = MemoryChunkEntity.builder().id("id-2").build();

            assertNotEquals(e1, e2);
        }
    }

    // ==================== NoArgsConstructor 测试 ====================

    @Nested
    @DisplayName("NoArgsConstructor")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("无参构造函数创建空实体")
        void noArgsConstructorCreatesEmptyEntity() {
            MemoryChunkEntity entity = new MemoryChunkEntity();

            assertNull(entity.getId());
            assertNull(entity.getFilePath());
            assertEquals(0, entity.getLineStart());
            assertEquals(0, entity.getLineEnd());
            assertNull(entity.getContent());
            assertEquals(MemoryChunkEntity.SCOPE_GLOBAL, entity.getScope());
            assertNull(entity.getAgentId());
            assertNull(entity.getCreatedAt());
            assertNull(entity.getUpdatedAt());
        }
    }

    // ==================== AllArgsConstructor 测试 ====================

    @Nested
    @DisplayName("AllArgsConstructor")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("全参构造函数创建完整实体")
        void allArgsConstructorCreatesFullEntity() {
            LocalDateTime now = LocalDateTime.now();

            MemoryChunkEntity entity = new MemoryChunkEntity(
                    "id-full",
                    "path.md",
                    1,
                    20,
                    "full content",
                    MemoryChunkEntity.SCOPE_AGENT,
                    "agent-a",
                    now,
                    now
            );

            assertEquals("id-full", entity.getId());
            assertEquals("path.md", entity.getFilePath());
            assertEquals(1, entity.getLineStart());
            assertEquals(20, entity.getLineEnd());
            assertEquals("full content", entity.getContent());
            assertEquals(MemoryChunkEntity.SCOPE_AGENT, entity.getScope());
            assertEquals("agent-a", entity.getAgentId());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now, entity.getUpdatedAt());
        }
    }
}
