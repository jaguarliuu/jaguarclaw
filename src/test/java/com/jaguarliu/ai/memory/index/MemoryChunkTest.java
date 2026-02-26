package com.jaguarliu.ai.memory.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryChunk 渐进式加载测试
 */
class MemoryChunkTest {

    @Test
    @DisplayName("L0 tokens should be estimated from title or content prefix")
    void testL0TokenEstimation() {
        MemoryChunk chunk = MemoryChunk.builder()
                .filePath("test.md")
                .content("This is a test content for memory chunk")
                .title("Test Title")
                .build();

        int l0Tokens = chunk.estimateL0Tokens();

        assertTrue(l0Tokens > 0, "L0 tokens should be positive");
        assertTrue(l0Tokens < 100, "L0 tokens should be reasonable (< 100)");
    }

    @Test
    @DisplayName("L1 tokens should include summary")
    void testL1TokenEstimation() {
        MemoryChunk chunk = MemoryChunk.builder()
                .filePath("test.md")
                .content("Test content")
                .title("Title")
                .summary("This is a summary of the content")
                .build();

        int l0Tokens = chunk.estimateL0Tokens();
        int l1Tokens = chunk.estimateL1Tokens();

        assertTrue(l1Tokens >= l0Tokens, "L1 should be >= L0");
    }

    @Test
    @DisplayName("L2 tokens should estimate full content")
    void testL2TokenEstimation() {
        String longContent = "a".repeat(1000);
        
        MemoryChunk chunk = MemoryChunk.builder()
                .filePath("test.md")
                .content(longContent)
                .title("Test")
                .build();

        int l2Tokens = chunk.estimateL2Tokens();

        assertTrue(l2Tokens > 500, "L2 tokens should reflect full content");
    }

    @Test
    @DisplayName("Priority should default to P2")
    void testDefaultPriority() {
        MemoryChunk chunk = MemoryChunk.builder()
                .filePath("test.md")
                .content("content")
                .build();

        assertEquals("P2", chunk.getPriority());
    }

    @Test
    @DisplayName("L0 < L1 < L2 token hierarchy")
    void testTokenHierarchy() {
        MemoryChunk chunk = MemoryChunk.builder()
                .filePath("test.md")
                .content("This is a longer content that spans multiple lines and contains more information for testing token estimation.")
                .title("Test Title")
                .summary("Summary of the content")
                .priority("P1")
                .build();

        int l0 = chunk.estimateL0Tokens();
        int l1 = chunk.estimateL1Tokens();
        int l2 = chunk.estimateL2Tokens();

        assertTrue(l0 <= l1, "L0 should <= L1");
        assertTrue(l1 <= l2, "L1 should <= L2");
    }
}
