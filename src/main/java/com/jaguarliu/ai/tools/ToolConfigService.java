package com.jaguarliu.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jaguarliu.ai.tools.search.SearchProviderRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 工具配置持久化 + 运行时热更新服务
 * 参照 LlmConfigService 模式：内存 + YAML 文件双写
 */
@Slf4j
@Service
public class ToolConfigService {

    private final ToolConfigProperties properties;
    private final SearchProviderRegistry searchProviderRegistry;

    public ToolConfigService(ToolConfigProperties properties,
                             @Lazy SearchProviderRegistry searchProviderRegistry) {
        this.properties = properties;
        this.searchProviderRegistry = searchProviderRegistry;
    }

    @Value("${jaguarclaw.config-dir:./data}")
    private String configDir;

    @PostConstruct
    void init() {
        loadFromFile();
    }

    /**
     * 获取当前配置（API Key 脱敏）
     */
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Trusted domains
        Map<String, Object> trustedDomains = new LinkedHashMap<>();
        trustedDomains.put("defaults", properties.getDefaultDomains());
        trustedDomains.put("user", properties.getUserDomains());
        result.put("trustedDomains", trustedDomains);

        // Search providers — 返回所有 6 种类型的当前状态
        List<Map<String, Object>> providers = new ArrayList<>();
        providers.add(buildProviderEntry("bing", "Bing Web Search", true));
        providers.add(buildProviderEntry("tavily", "Tavily", true));
        providers.add(buildProviderEntry("perplexity", "Perplexity", true));
        providers.add(buildProviderEntry("github", "GitHub Search", false));
        providers.add(buildProviderEntry("arxiv", "arXiv", false));
        result.put("searchProviders", providers);

        // HITL configuration
        Map<String, Object> hitl = new LinkedHashMap<>();
        hitl.put("alwaysConfirmTools", properties.getAlwaysConfirmTools());
        hitl.put("dangerousKeywords", properties.getDangerousKeywords());
        result.put("hitl", hitl);

        Map<String, Object> delivery = new LinkedHashMap<>();

        // Email tool configuration
        Map<String, Object> email = new LinkedHashMap<>();
        ToolConfigProperties.EmailToolConfig emailCfg = properties.getEmail();
        email.put("enabled", emailCfg != null && emailCfg.isEnabled());
        email.put("host", emailCfg != null ? safe(emailCfg.getHost()) : "");
        email.put("port", emailCfg != null && emailCfg.getPort() != null ? emailCfg.getPort() : 587);
        email.put("username", emailCfg != null ? safe(emailCfg.getUsername()) : "");
        email.put("from", emailCfg != null ? safe(emailCfg.getFrom()) : "");
        email.put("tls", emailCfg != null && emailCfg.isTls());
        email.put("password", emailCfg != null ? maskApiKey(emailCfg.getPassword()) : "");
        email.put("configured", isEmailConfigured(emailCfg));
        delivery.put("email", email);

        // Webhook tool configuration
        Map<String, Object> webhook = new LinkedHashMap<>();
        ToolConfigProperties.WebhookToolConfig webhookCfg = properties.getWebhook();
        webhook.put("enabled", webhookCfg != null && webhookCfg.isEnabled());
        List<Map<String, Object>> endpoints = new ArrayList<>();
        if (webhookCfg != null && webhookCfg.getEndpoints() != null) {
            for (ToolConfigProperties.WebhookEndpoint ep : webhookCfg.getEndpoints()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("alias", safe(ep.getAlias()));
                item.put("url", safe(ep.getUrl()));
                item.put("method", safe(ep.getMethod()).isBlank() ? "POST" : ep.getMethod().toUpperCase(Locale.ROOT));
                item.put("headers", ep.getHeaders() != null ? ep.getHeaders() : Map.of());
                item.put("trigger", safe(ep.getTrigger()));
                item.put("enabled", ep.isEnabled());
                endpoints.add(item);
            }
        }
        webhook.put("endpoints", endpoints);
        webhook.put("configured", !endpoints.isEmpty());
        delivery.put("webhook", webhook);
        result.put("delivery", delivery);

        return result;
    }

    /**
     * 保存配置：写文件 + 更新内存
     */
    @SuppressWarnings("unchecked")
    public void saveConfig(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        Map<String, Object> deliveryParams = params.containsKey("delivery")
                ? (Map<String, Object>) params.get("delivery")
                : params;

        // 更新用户域名
        if (params.containsKey("userDomains")) {
            List<String> userDomains = (List<String>) params.get("userDomains");
            properties.setUserDomains(userDomains != null ? new ArrayList<>(userDomains) : new ArrayList<>());
        }

        // 更新搜索引擎提供商
        if (params.containsKey("searchProviders")) {
            List<Map<String, Object>> providerList = (List<Map<String, Object>>) params.get("searchProviders");
            List<ToolConfigProperties.SearchProviderConfig> configs = new ArrayList<>();
            if (providerList != null) {
                for (Map<String, Object> p : providerList) {
                    configs.add(ToolConfigProperties.SearchProviderConfig.builder()
                            .type((String) p.get("type"))
                            .apiKey((String) p.get("apiKey"))
                            .enabled(Boolean.TRUE.equals(p.get("enabled")))
                            .build());
                }
            }
            properties.setSearchProviders(configs);
        }

        // 更新 HITL 配置
        if (params.containsKey("hitl")) {
            Map<String, Object> hitl = (Map<String, Object>) params.get("hitl");
            if (hitl != null) {
                if (hitl.containsKey("alwaysConfirmTools")) {
                    List<String> tools = (List<String>) hitl.get("alwaysConfirmTools");
                    properties.setAlwaysConfirmTools(tools != null ? new ArrayList<>(tools) : new ArrayList<>());
                }
                if (hitl.containsKey("dangerousKeywords")) {
                    List<String> keywords = (List<String>) hitl.get("dangerousKeywords");
                    properties.setDangerousKeywords(keywords != null ? new ArrayList<>(keywords) : new ArrayList<>());
                }
            }
        }

        // 更新 Email 工具配置
        if (deliveryParams != null && deliveryParams.containsKey("email")) {
            Map<String, Object> emailMap = (Map<String, Object>) deliveryParams.get("email");
            if (emailMap != null) {
                ToolConfigProperties.EmailToolConfig current = properties.getEmail();
                if (current == null) {
                    current = new ToolConfigProperties.EmailToolConfig();
                }
                current.setEnabled(Boolean.TRUE.equals(emailMap.get("enabled")));
                current.setHost(stringOrNull(emailMap.get("host")));
                Object portObj = emailMap.get("port");
                if (portObj instanceof Number n) {
                    current.setPort(n.intValue());
                } else if (portObj instanceof String s && !s.isBlank()) {
                    current.setPort(Integer.parseInt(s));
                } else if (current.getPort() == null) {
                    current.setPort(587);
                }
                current.setUsername(stringOrNull(emailMap.get("username")));
                current.setFrom(stringOrNull(emailMap.get("from")));
                current.setTls(!emailMap.containsKey("tls") || Boolean.TRUE.equals(emailMap.get("tls")));

                String incomingPassword = stringOrNull(emailMap.get("password"));
                if (incomingPassword != null && !incomingPassword.isBlank() && !incomingPassword.contains("***")) {
                    current.setPassword(incomingPassword);
                } else if (incomingPassword != null && incomingPassword.isBlank()) {
                    current.setPassword(null);
                }

                properties.setEmail(current);
            }
        }

        // 更新 Webhook 工具配置
        if (deliveryParams != null && deliveryParams.containsKey("webhook")) {
            Map<String, Object> webhookMap = (Map<String, Object>) deliveryParams.get("webhook");
            if (webhookMap != null) {
                ToolConfigProperties.WebhookToolConfig webhookCfg = properties.getWebhook();
                if (webhookCfg == null) {
                    webhookCfg = new ToolConfigProperties.WebhookToolConfig();
                }

                webhookCfg.setEnabled(Boolean.TRUE.equals(webhookMap.get("enabled")));
                Object endpointsObj = webhookMap.get("endpoints");
                List<ToolConfigProperties.WebhookEndpoint> newEndpoints = new ArrayList<>();
                if (endpointsObj instanceof List<?> endpointList) {
                    for (Object item : endpointList) {
                        if (!(item instanceof Map<?, ?> raw)) {
                            continue;
                        }
                        String alias = stringOrNull(raw.get("alias"));
                        String url = stringOrNull(raw.get("url"));
                        if (alias == null || alias.isBlank() || url == null || url.isBlank()) {
                            continue;
                        }

                        String method = Optional.ofNullable(stringOrNull(raw.get("method")))
                                .filter(s -> !s.isBlank())
                                .orElse("POST")
                                .toUpperCase(Locale.ROOT);

                        Map<String, String> headers = new LinkedHashMap<>();
                        Object headersObj = raw.get("headers");
                        if (headersObj instanceof Map<?, ?> headerMap) {
                            for (Map.Entry<?, ?> h : headerMap.entrySet()) {
                                if (h.getKey() != null && h.getValue() != null) {
                                    headers.put(String.valueOf(h.getKey()), String.valueOf(h.getValue()));
                                }
                            }
                        }

                        boolean endpointEnabled = !raw.containsKey("enabled") || Boolean.TRUE.equals(raw.get("enabled"));
                        String trigger = stringOrNull(raw.get("trigger"));

                        newEndpoints.add(ToolConfigProperties.WebhookEndpoint.builder()
                                .alias(alias)
                                .url(url)
                                .method(method)
                                .headers(headers)
                                .trigger(trigger)
                                .enabled(endpointEnabled)
                                .build());
                    }
                }

                webhookCfg.setEndpoints(newEndpoints);
                properties.setWebhook(webhookCfg);
            }
        }

        // 持久化到文件
        writeConfigFile();

        // 重建搜索引擎注册表
        searchProviderRegistry.rebuild();

        log.info("Tool config saved: {} user domains, {} search providers",
                properties.getUserDomains().size(), properties.getSearchProviders().size());
    }

    /**
     * 持久化当前内存配置到文件（不修改内存状态）
     */
    public void persistCurrentConfig() {
        writeConfigFile();
        searchProviderRegistry.rebuild();
    }

    // ==================== Private ====================

    private void loadFromFile() {
        File configFile = new File(configDir, "tool-config.yml");
        if (!configFile.exists()) {
            log.info("No tool config file found at {}", configFile.getAbsolutePath());
            return;
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> config = yamlMapper.readValue(configFile, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> deliveryConfig = config.containsKey("delivery")
                    ? (Map<String, Object>) config.get("delivery")
                    : config;

            if (config.containsKey("userDomains")) {
                @SuppressWarnings("unchecked")
                List<String> domains = (List<String>) config.get("userDomains");
                if (domains != null) {
                    properties.setUserDomains(new ArrayList<>(domains));
                }
            }

            if (config.containsKey("searchProviders")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> providerList = (List<Map<String, Object>>) config.get("searchProviders");
                if (providerList != null) {
                    List<ToolConfigProperties.SearchProviderConfig> configs = new ArrayList<>();
                    for (Map<String, Object> p : providerList) {
                        configs.add(ToolConfigProperties.SearchProviderConfig.builder()
                                .type((String) p.get("type"))
                                .apiKey((String) p.get("apiKey"))
                                .enabled(Boolean.TRUE.equals(p.get("enabled")))
                                .build());
                    }
                    properties.setSearchProviders(configs);
                }
            }

            if (config.containsKey("hitl")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hitl = (Map<String, Object>) config.get("hitl");
                if (hitl != null) {
                    if (hitl.containsKey("alwaysConfirmTools")) {
                        @SuppressWarnings("unchecked")
                        List<String> tools = (List<String>) hitl.get("alwaysConfirmTools");
                        if (tools != null) {
                            properties.setAlwaysConfirmTools(new ArrayList<>(tools));
                        }
                    }
                    if (hitl.containsKey("dangerousKeywords")) {
                        @SuppressWarnings("unchecked")
                        List<String> keywords = (List<String>) hitl.get("dangerousKeywords");
                        if (keywords != null) {
                            properties.setDangerousKeywords(new ArrayList<>(keywords));
                        }
                    }
                }
            }

            if (deliveryConfig != null && deliveryConfig.containsKey("email")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> email = (Map<String, Object>) deliveryConfig.get("email");
                if (email != null) {
                    ToolConfigProperties.EmailToolConfig emailCfg = new ToolConfigProperties.EmailToolConfig();
                    emailCfg.setEnabled(Boolean.TRUE.equals(email.get("enabled")));
                    emailCfg.setHost(stringOrNull(email.get("host")));
                    Object portObj = email.get("port");
                    if (portObj instanceof Number n) {
                        emailCfg.setPort(n.intValue());
                    } else if (portObj instanceof String s && !s.isBlank()) {
                        emailCfg.setPort(Integer.parseInt(s));
                    }
                    if (emailCfg.getPort() == null) {
                        emailCfg.setPort(587);
                    }
                    emailCfg.setUsername(stringOrNull(email.get("username")));
                    emailCfg.setFrom(stringOrNull(email.get("from")));
                    emailCfg.setTls(!email.containsKey("tls") || Boolean.TRUE.equals(email.get("tls")));
                    emailCfg.setPassword(stringOrNull(email.get("password")));
                    properties.setEmail(emailCfg);
                }
            }

            if (deliveryConfig != null && deliveryConfig.containsKey("webhook")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> webhook = (Map<String, Object>) deliveryConfig.get("webhook");
                if (webhook != null) {
                    ToolConfigProperties.WebhookToolConfig webhookCfg = new ToolConfigProperties.WebhookToolConfig();
                    webhookCfg.setEnabled(Boolean.TRUE.equals(webhook.get("enabled")));
                    List<ToolConfigProperties.WebhookEndpoint> endpoints = new ArrayList<>();
                    Object endpointsObj = webhook.get("endpoints");
                    if (endpointsObj instanceof List<?> endpointList) {
                        for (Object item : endpointList) {
                            if (!(item instanceof Map<?, ?> raw)) {
                                continue;
                            }
                            String alias = stringOrNull(raw.get("alias"));
                            String url = stringOrNull(raw.get("url"));
                            if (alias == null || alias.isBlank() || url == null || url.isBlank()) {
                                continue;
                            }
                            String method = Optional.ofNullable(stringOrNull(raw.get("method")))
                                    .filter(s -> !s.isBlank())
                                    .orElse("POST")
                                    .toUpperCase(Locale.ROOT);

                            Map<String, String> headers = new LinkedHashMap<>();
                            Object headersObj = raw.get("headers");
                            if (headersObj instanceof Map<?, ?> headerMap) {
                                for (Map.Entry<?, ?> h : headerMap.entrySet()) {
                                    if (h.getKey() != null && h.getValue() != null) {
                                        headers.put(String.valueOf(h.getKey()), String.valueOf(h.getValue()));
                                    }
                                }
                            }

                            boolean endpointEnabled = !raw.containsKey("enabled") || Boolean.TRUE.equals(raw.get("enabled"));
                            endpoints.add(ToolConfigProperties.WebhookEndpoint.builder()
                                    .alias(alias)
                                    .url(url)
                                    .method(method)
                                    .headers(headers)
                                    .trigger(stringOrNull(raw.get("trigger")))
                                    .enabled(endpointEnabled)
                                    .build());
                        }
                    }
                    webhookCfg.setEndpoints(endpoints);
                    properties.setWebhook(webhookCfg);
                }
            }

            log.info("Loaded tool config from file: {} user domains, {} search providers",
                    properties.getUserDomains().size(), properties.getSearchProviders().size());
        } catch (Exception e) {
            log.warn("Failed to load tool config file: {}", e.getMessage());
        }
    }

    private void writeConfigFile() {
        try {
            File dir = new File(configDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File configFile = new File(dir, "tool-config.yml");
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("userDomains", properties.getUserDomains());

            List<Map<String, Object>> providers = new ArrayList<>();
            for (ToolConfigProperties.SearchProviderConfig p : properties.getSearchProviders()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", p.getType());
                entry.put("apiKey", p.getApiKey());
                entry.put("enabled", p.isEnabled());
                providers.add(entry);
            }
            config.put("searchProviders", providers);

            // HITL configuration
            Map<String, Object> hitl = new LinkedHashMap<>();
            hitl.put("alwaysConfirmTools", properties.getAlwaysConfirmTools());
            hitl.put("dangerousKeywords", properties.getDangerousKeywords());
            config.put("hitl", hitl);

            ToolConfigProperties.EmailToolConfig email = properties.getEmail();
            Map<String, Object> delivery = new LinkedHashMap<>();
            if (email != null) {
                Map<String, Object> emailMap = new LinkedHashMap<>();
                emailMap.put("enabled", email.isEnabled());
                emailMap.put("host", safe(email.getHost()));
                emailMap.put("port", email.getPort() != null ? email.getPort() : 587);
                emailMap.put("username", safe(email.getUsername()));
                emailMap.put("from", safe(email.getFrom()));
                emailMap.put("tls", email.isTls());
                emailMap.put("password", safe(email.getPassword()));
                delivery.put("email", emailMap);
            }

            ToolConfigProperties.WebhookToolConfig webhook = properties.getWebhook();
            if (webhook != null) {
                Map<String, Object> webhookMap = new LinkedHashMap<>();
                webhookMap.put("enabled", webhook.isEnabled());
                List<Map<String, Object>> endpointList = new ArrayList<>();
                if (webhook.getEndpoints() != null) {
                    for (ToolConfigProperties.WebhookEndpoint endpoint : webhook.getEndpoints()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("alias", safe(endpoint.getAlias()));
                        entry.put("url", safe(endpoint.getUrl()));
                        entry.put("method", safe(endpoint.getMethod()).isBlank() ? "POST" : endpoint.getMethod().toUpperCase(Locale.ROOT));
                        entry.put("headers", endpoint.getHeaders() != null ? endpoint.getHeaders() : Map.of());
                        entry.put("trigger", safe(endpoint.getTrigger()));
                        entry.put("enabled", endpoint.isEnabled());
                        endpointList.add(entry);
                    }
                }
                webhookMap.put("endpoints", endpointList);
                delivery.put("webhook", webhookMap);
            }

            if (!delivery.isEmpty()) {
                config.put("delivery", delivery);
            }

            yamlMapper.writeValue(configFile, config);
            log.info("Tool config file written to {}", configFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write tool config file", e);
            throw new RuntimeException("Failed to save tool config: " + e.getMessage());
        }
    }

    /**
     * 构建单个提供商的 GET 响应条目
     */
    private Map<String, Object> buildProviderEntry(String type, String displayName, boolean keyRequired) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("displayName", displayName);
        entry.put("keyRequired", keyRequired);

        // 添加获取 API Key 的链接
        entry.put("apiKeyUrl", getApiKeyUrl(type));

        // 从当前配置中查找该类型的状态
        Optional<ToolConfigProperties.SearchProviderConfig> existing = properties.getSearchProviders().stream()
                .filter(p -> type.equals(p.getType()))
                .findFirst();

        if (existing.isPresent()) {
            entry.put("apiKey", maskApiKey(existing.get().getApiKey()));
            entry.put("enabled", existing.get().isEnabled());
        } else {
            entry.put("apiKey", "");
            entry.put("enabled", false);
        }

        return entry;
    }

    /**
     * 获取各个提供商的 API Key 申请地址
     */
    private String getApiKeyUrl(String type) {
        return switch (type) {
            case "bing" -> "https://www.microsoft.com/en-us/bing/apis/bing-web-search-api";
            case "tavily" -> "https://tavily.com/#api";
            case "perplexity" -> "https://www.perplexity.ai/settings/api";
            case "github" -> "https://github.com/settings/tokens";
            case "arxiv" -> ""; // arXiv 不需要 API key
            default -> "";
        };
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 3) + "***" + apiKey.substring(apiKey.length() - 3);
    }

    private boolean isEmailConfigured(ToolConfigProperties.EmailToolConfig config) {
        if (config == null) {
            return false;
        }
        return notBlank(config.getHost())
                && config.getPort() != null
                && notBlank(config.getUsername())
                && notBlank(config.getFrom())
                && notBlank(config.getPassword());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }
}
