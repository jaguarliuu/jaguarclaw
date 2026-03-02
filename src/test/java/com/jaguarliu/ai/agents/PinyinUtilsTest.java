package com.jaguarliu.ai.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PinyinUtils Tests")
class PinyinUtilsTest {

    @Test
    @DisplayName("纯中文转拼音")
    void chineseToSlug() {
        assertEquals("zhushou", PinyinUtils.toSlug("助手"));
        assertEquals("daima", PinyinUtils.toSlug("代码"));
    }

    @Test
    @DisplayName("中英混合")
    void mixedToSlug() {
        assertEquals("my-zhushou", PinyinUtils.toSlug("My助手"));
        assertEquals("code-daima-agent", PinyinUtils.toSlug("Code代码Agent"));
    }

    @Test
    @DisplayName("纯英文小写保留")
    void englishPassthrough() {
        assertEquals("planner", PinyinUtils.toSlug("planner"));
        assertEquals("my-agent", PinyinUtils.toSlug("My Agent"));
    }

    @Test
    @DisplayName("特殊字符转连字符")
    void specialCharsToHyphen() {
        assertEquals("hello-world", PinyinUtils.toSlug("hello, world!"));
        assertEquals("test-agent", PinyinUtils.toSlug("test_agent"));
    }

    @Test
    @DisplayName("空或null返回agent")
    void emptyFallback() {
        assertEquals("agent", PinyinUtils.toSlug(null));
        assertEquals("agent", PinyinUtils.toSlug(""));
        assertEquals("agent", PinyinUtils.toSlug("   "));
        assertEquals("agent", PinyinUtils.toSlug("!!!"));
    }

    @Test
    @DisplayName("超长输入截断到50字符")
    void truncation() {
        String longName = "这是一段非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常长的名字";
        String result = PinyinUtils.toSlug(longName);
        assertTrue(result.length() <= 50);
        assertFalse(result.endsWith("-"));
    }
}
