package com.jaguarliu.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具配置属性（内存态）
 * 包含 HTTP 可信域名列表和搜索引擎提供商配置
 */
@Data
@Component
public class ToolConfigProperties {

    /**
     * 默认可信域名（始终允许，用户不可编辑）
     */
    private List<String> defaultDomains = List.of(
            "baidu.com", "google.com", "github.com", "githubusercontent.com",
            "stackoverflow.com", "wikipedia.org", "npmjs.com",
            "maven.org", "pypi.org", "docs.oracle.com");

    /**
     * 用户添加的可信域名（通过设置页编辑）
     */
    private List<String> userDomains = new ArrayList<>();

    /**
     * 搜索引擎提供商配置
     */
    private List<SearchProviderConfig> searchProviders = new ArrayList<>();

    /**
     * 始终需要 HITL 确认的工具名称（用户配置）
     * 例如: ["shell", "shell_start", "write_file"]
     */
    private List<String> alwaysConfirmTools = new ArrayList<>();

    /**
     * 用户自定义的危险命令关键词
     * 命令中包含任一关键词即触发 HITL 确认（大小写不敏感的子串匹配）
     * 例如: ["docker rm", "npm publish", "DROP TABLE"]
     */
    private List<String> dangerousKeywords = new ArrayList<>();

    /**
     * 用户配置的可信读取目录（绝对路径列表）
     * agent 可以读取这些目录下的任意文件，无需 HITL 确认
     */
    private List<String> trustedReadPaths = new ArrayList<>();

    private Integer timeout = 60;

    /**
     * 邮件工具配置
     */
    private EmailToolConfig email = new EmailToolConfig();

    /**
     * Webhook 工具配置
     */
    private WebhookToolConfig webhook = new WebhookToolConfig();

    /**
     * 搜索结果发现的域名（临时白名单，session 结束时清除）
     * 使用 ConcurrentHashMap.newKeySet() 保证线程安全
     */
    private final Set<String> searchDiscoveredDomains = ConcurrentHashMap.newKeySet();

    /**
     * 检查主机名是否在可信域名列表中
     * 匹配规则：精确匹配 或 以 .domain 结尾
     * 检查顺序：默认域名 → 用户域名 → 搜索发现域名
     */
    public boolean isDomainTrusted(String host) {
        if (host == null)
            return false;
        String h = host.toLowerCase();
        return defaultDomains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d))
                || userDomains.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d))
                || searchDiscoveredDomains.contains(h);
    }

    /**
     * 注册搜索结果发现的域名到临时白名单
     */
    public void addSearchDiscoveredDomains(Collection<String> domains) {
        searchDiscoveredDomains.addAll(domains);
    }

    /**
     * 清除搜索结果临时白名单（session/run 结束时调用）
     */
    public void clearSearchDiscoveredDomains() {
        searchDiscoveredDomains.clear();
    }

    /**
     * 检查工具是否在用户配置的"始终确认"列表中
     */
    public boolean isAlwaysConfirmTool(String toolName) {
        return alwaysConfirmTools.stream()
                .anyMatch(t -> t.equalsIgnoreCase(toolName));
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SearchProviderConfig {
        /**
         * 提供商类型：bing, tavily, perplexity, github, arxiv
         */
        private String type;

        /**
         * API Key（免费提供商为空）
         */
        private String apiKey;

        /**
         * 是否启用
         */
        private boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailToolConfig {
        /**
         * 是否启用邮件工具
         */
        private boolean enabled = false;

        private String host;
        private Integer port = 587;
        private String username;
        private String from;
        private boolean tls = true;

        /**
         * SMTP 密码（明文存储在 tool-config.yml，和搜索 API key 一致）
         */
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookToolConfig {
        /**
         * 是否启用 webhook 工具
         */
        private boolean enabled = false;

        /**
         * 可触发 webhook 目标列表（alias 唯一）
         */
        private List<WebhookEndpoint> endpoints = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookEndpoint {
        /**
         * webhook 别名（自然语言可引用）
         */
        private String alias;

        private String url;

        @Builder.Default
        private String method = "POST";

        @Builder.Default
        private Map<String, String> headers = new LinkedHashMap<>();

        /**
         * 触发机制描述（例如 "日报推送"、"异常告警"）
         */
        private String trigger;

        @Builder.Default
        private boolean enabled = true;
    }
}
