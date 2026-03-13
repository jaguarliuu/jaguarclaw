package com.jaguarliu.ai.tools.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DeliveryToolService Tests")
class DeliveryToolServiceTest {

    @Test
    @DisplayName("markdown 邮件正文应渲染为 html")
    void shouldRenderMarkdownBodyAsHtml() {
        String html = DeliveryToolService.renderEmailBody("""
                # 巡检报告

                - CPU 正常
                - 内存正常

                | 主机 | 状态 |
                | --- | --- |
                | node-1 | OK |
                """);

        assertTrue(html.contains("<h1>巡检报告</h1>"));
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>CPU 正常</li>"));
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<td>node-1</td>"));
    }

    @Test
    @DisplayName("已有 html 邮件正文应原样保留")
    void shouldKeepExistingHtmlBody() {
        String source = "<h1>巡检报告</h1><p>全部正常</p>";

        String html = DeliveryToolService.renderEmailBody(source);

        assertEquals(source, html);
    }

    @Test
    @DisplayName("纯文本换行应保留为 html 换行")
    void shouldPreserveLineBreaksForPlainText() {
        String html = DeliveryToolService.renderEmailBody("第一行\n第二行");

        assertTrue(html.contains("第一行"));
        assertTrue(html.contains("<br"));
        assertTrue(html.contains("第二行"));
    }
}
