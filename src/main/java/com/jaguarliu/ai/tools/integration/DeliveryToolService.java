package com.jaguarliu.ai.tools.integration;

import com.jaguarliu.ai.tools.ToolConfigProperties;
import com.jaguarliu.ai.tools.ToolConfigService;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryToolService {

    private static final int MAX_RESPONSE_LENGTH = 32000;

    private final ToolConfigProperties toolConfigProperties;
    private final ToolConfigService toolConfigService;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    public boolean isEmailToolEnabled() {
        ToolConfigProperties.EmailToolConfig config = toolConfigProperties.getEmail();
        return config != null
                && config.isEnabled()
                && notBlank(config.getHost())
                && config.getPort() != null
                && notBlank(config.getUsername())
                && notBlank(config.getFrom())
                && notBlank(config.getPassword());
    }

    public boolean isWebhookToolEnabled() {
        ToolConfigProperties.WebhookToolConfig config = toolConfigProperties.getWebhook();
        return config != null
                && config.isEnabled()
                && config.getEndpoints() != null
                && config.getEndpoints().stream().anyMatch(ToolConfigProperties.WebhookEndpoint::isEnabled);
    }

    public boolean isWebhookToolActivated() {
        ToolConfigProperties.WebhookToolConfig config = toolConfigProperties.getWebhook();
        return config != null && config.isEnabled();
    }

    public String sendEmail(String to, String subject, String body, String cc) {
        if (!isEmailToolEnabled()) {
            throw new IllegalStateException("Email tool is not configured or disabled");
        }

        ToolConfigProperties.EmailToolConfig config = toolConfigProperties.getEmail();
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", config.getHost());
            props.put("mail.smtp.port", String.valueOf(config.getPort()));
            props.put("mail.smtp.auth", "true");
            if (config.isTls()) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "30000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getFrom()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (cc != null && !cc.isBlank()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setSubject(subject, "UTF-8");

            boolean looksLikeHtml = body != null && body.contains("<") && body.contains(">");
            String htmlBody = looksLikeHtml ? body : escapeHtml(body);
            message.setContent(htmlBody, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("Email sent by delivery tool to {}", to);
            return "Email sent successfully to " + to;
        } catch (Exception e) {
            log.error("Failed to send email via delivery tool: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    public String sendWebhook(String target, String payload, String triggerHint) {
        if (!isWebhookToolEnabled()) {
            throw new IllegalStateException("Webhook tool is not configured or disabled");
        }

        ToolConfigProperties.WebhookEndpoint endpoint = resolveWebhookTarget(target, triggerHint);
        String method = normalizeMethod(endpoint.getMethod());
        Map<String, String> headers = endpoint.getHeaders() != null ? endpoint.getHeaders() : Map.of();

        try {
            var requestSpec = "PUT".equalsIgnoreCase(method)
                    ? webClient.put().uri(endpoint.getUrl())
                    : webClient.post().uri(endpoint.getUrl());

            for (Map.Entry<String, String> h : headers.entrySet()) {
                requestSpec = requestSpec.header(h.getKey(), h.getValue());
            }

            String responseBody = requestSpec
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (responseBody != null && responseBody.length() > MAX_RESPONSE_LENGTH) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_LENGTH) + "\n[truncated]";
            }

            log.info("Webhook sent by alias '{}' to {}", endpoint.getAlias(), endpoint.getUrl());
            return "Webhook [" + endpoint.getAlias() + "] sent successfully. Response: "
                    + (responseBody != null ? responseBody : "(empty)");
        } catch (Exception e) {
            log.error("Failed to send webhook by alias '{}': {}", endpoint.getAlias(), e.getMessage(), e);
            throw new RuntimeException("Failed to send webhook: " + e.getMessage(), e);
        }
    }

    public String upsertWebhook(String alias, String url, String method,
                                Map<String, String> headers, String trigger, Boolean enabled) {
        if (!notBlank(alias)) {
            throw new IllegalArgumentException("alias is required");
        }
        if (!notBlank(url)) {
            throw new IllegalArgumentException("url is required");
        }

        validateUrl(url);
        String normalizedMethod = normalizeMethod(method);

        ToolConfigProperties.WebhookToolConfig webhook = toolConfigProperties.getWebhook();
        if (webhook == null) {
            webhook = new ToolConfigProperties.WebhookToolConfig();
            toolConfigProperties.setWebhook(webhook);
        }
        if (webhook.getEndpoints() == null) {
            webhook.setEndpoints(new ArrayList<>());
        }

        ToolConfigProperties.WebhookEndpoint endpoint = ToolConfigProperties.WebhookEndpoint.builder()
                .alias(alias.trim())
                .url(url.trim())
                .method(normalizedMethod)
                .headers(headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>())
                .trigger(trigger != null ? trigger.trim() : null)
                .enabled(enabled == null || enabled)
                .build();

        List<ToolConfigProperties.WebhookEndpoint> endpoints = webhook.getEndpoints();
        int index = indexOfAlias(endpoints, alias);
        boolean created;
        if (index >= 0) {
            endpoints.set(index, endpoint);
            created = false;
        } else {
            endpoints.add(endpoint);
            created = true;
        }

        if (!webhook.isEnabled()) {
            webhook.setEnabled(true);
        }
        toolConfigService.persistCurrentConfig();

        return created
                ? "Webhook saved: " + alias + " -> " + url
                : "Webhook updated: " + alias + " -> " + url;
    }

    public String removeWebhook(String alias) {
        if (!notBlank(alias)) {
            throw new IllegalArgumentException("alias is required");
        }
        ToolConfigProperties.WebhookToolConfig webhook = toolConfigProperties.getWebhook();
        if (webhook == null || webhook.getEndpoints() == null || webhook.getEndpoints().isEmpty()) {
            return "No webhook endpoints configured.";
        }
        int before = webhook.getEndpoints().size();
        webhook.getEndpoints().removeIf(ep -> alias.equalsIgnoreCase(ep.getAlias()));
        if (webhook.getEndpoints().size() == before) {
            return "Webhook alias not found: " + alias;
        }

        toolConfigService.persistCurrentConfig();
        return "Webhook removed: " + alias;
    }

    public List<Map<String, Object>> listWebhooks() {
        ToolConfigProperties.WebhookToolConfig webhook = toolConfigProperties.getWebhook();
        if (webhook == null || webhook.getEndpoints() == null) {
            return List.of();
        }
        return webhook.getEndpoints().stream()
                .map(ep -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alias", ep.getAlias());
                    item.put("url", ep.getUrl());
                    item.put("method", normalizeMethod(ep.getMethod()));
                    item.put("trigger", ep.getTrigger());
                    item.put("enabled", ep.isEnabled());
                    item.put("headers", ep.getHeaders() != null ? ep.getHeaders() : Map.of());
                    return item;
                })
                .toList();
    }

    public String describeWebhookAliases() {
        List<String> aliases = listWebhooks().stream()
                .filter(item -> Boolean.TRUE.equals(item.get("enabled")))
                .map(item -> Objects.toString(item.get("alias"), ""))
                .filter(s -> !s.isBlank())
                .toList();
        if (aliases.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", aliases);
    }

    private ToolConfigProperties.WebhookEndpoint resolveWebhookTarget(String target, String triggerHint) {
        List<ToolConfigProperties.WebhookEndpoint> endpoints = Optional
                .ofNullable(toolConfigProperties.getWebhook())
                .map(ToolConfigProperties.WebhookToolConfig::getEndpoints)
                .orElse(List.of());

        List<ToolConfigProperties.WebhookEndpoint> enabledEndpoints = endpoints.stream()
                .filter(ToolConfigProperties.WebhookEndpoint::isEnabled)
                .toList();

        if (enabledEndpoints.isEmpty()) {
            throw new IllegalArgumentException("No enabled webhook endpoints configured");
        }

        if (notBlank(target)) {
            String normalized = target.trim();
            Optional<ToolConfigProperties.WebhookEndpoint> byAlias = enabledEndpoints.stream()
                    .filter(ep -> normalized.equalsIgnoreCase(ep.getAlias()))
                    .findFirst();
            if (byAlias.isPresent()) {
                return byAlias.get();
            }

            Optional<ToolConfigProperties.WebhookEndpoint> byUrl = enabledEndpoints.stream()
                    .filter(ep -> normalized.equalsIgnoreCase(ep.getUrl()))
                    .findFirst();
            if (byUrl.isPresent()) {
                return byUrl.get();
            }

            Optional<ToolConfigProperties.WebhookEndpoint> byTrigger = enabledEndpoints.stream()
                    .filter(ep -> notBlank(ep.getTrigger()) && containsIgnoreCase(ep.getTrigger(), normalized))
                    .findFirst();
            if (byTrigger.isPresent()) {
                return byTrigger.get();
            }

            String availableAliases = enabledEndpoints.stream()
                    .map(ToolConfigProperties.WebhookEndpoint::getAlias)
                    .filter(this::notBlank)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(none)");
            throw new IllegalArgumentException("Webhook target not found: " + target + ". Available aliases: " + availableAliases);
        }

        if (notBlank(triggerHint)) {
            Optional<ToolConfigProperties.WebhookEndpoint> byTrigger = enabledEndpoints.stream()
                    .filter(ep -> notBlank(ep.getTrigger()) && containsIgnoreCase(ep.getTrigger(), triggerHint))
                    .findFirst();
            if (byTrigger.isPresent()) {
                return byTrigger.get();
            }
        }

        return enabledEndpoints.get(0);
    }

    private int indexOfAlias(List<ToolConfigProperties.WebhookEndpoint> endpoints, String alias) {
        for (int i = 0; i < endpoints.size(); i++) {
            if (alias.equalsIgnoreCase(endpoints.get(i).getAlias())) {
                return i;
            }
        }
        return -1;
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid webhook URL: " + url);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + url);
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "POST";
        }
        String normalized = method.trim().toUpperCase();
        if (!"POST".equals(normalized) && !"PUT".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported method: " + method + " (only POST/PUT are supported)");
        }
        return normalized;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>\n");
    }

    private boolean containsIgnoreCase(String source, String token) {
        return source.toLowerCase().contains(token.toLowerCase());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
