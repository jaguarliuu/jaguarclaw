package com.jaguarliu.ai.nodeconsole;

import com.jcraft.jsch.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;

/**
 * SSH 连接器
 * 通过 JSch 执行远程 SSH 命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshConnector implements Connector {

    private static final int DEFAULT_SSH_CONNECT_TIMEOUT_MS = 10000; // 10秒连接超时
    private static final int DEFAULT_SSH_IDLE_SECONDS = 300; // 5分钟
    private static final int BUFFER_SIZE = 4096;

    private final NodeConsoleProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, SessionHolder> sessionCache = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "ssh";
    }

    @Override
    public ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options) {
        // 使用 Future 实现硬超时
        Future<ExecResult> future = executor.submit(() ->
                executeInternal(credential, node, command, options));

        try {
            // 硬超时：如果超过 timeoutSeconds，抛出 TimeoutException
            return future.get(options.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // 尝试中断任务
            return new ExecResult.Builder()
                .stderr("Command execution timed out after " + options.getTimeoutSeconds() + " seconds")
                .exitCode(-1)
                .timedOut(true)
                .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecResult.Builder()
                .stderr("Command execution interrupted")
                .exitCode(-1)
                .build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Command execution failed", cause);
        }
    }

    private ExecResult executeInternal(String credential, NodeEntity node, String command, ExecOptions options) {
        if (!isConnectionReuseEnabled()) {
            return executeWithoutReuse(credential, node, command, options);
        }
        return executeWithReuse(credential, node, command, options);
    }

    private ExecResult executeWithoutReuse(String credential, NodeEntity node, String command, ExecOptions options) {
        Session session = null;
        try {
            int timeoutMs = resolveConnectTimeoutMs();
            session = createSession(credential, node, timeoutMs);
            session.connect(timeoutMs);
            return executeOverSession(session, command, options);

        } catch (InterruptedException e) {
            // 超时中断
            return new ExecResult.Builder()
                    .stderr("Execution interrupted by timeout")
                    .exitCode(-1)
                    .timedOut(true)
                    .errorType(ExecResult.ErrorType.TIMEOUT)
                    .build();
        } catch (com.jcraft.jsch.JSchException e) {
            // JSch 特定异常 - 映射到具体错误类型
            log.error("SSH execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            log.debug("SSH execute exception details for node {}", node.getAlias(), e);
            ExecResult.ErrorType errorType = mapJSchException(e);
            return new ExecResult.Builder()
                    .stderr("SSH execution failed: " + e.getClass().getSimpleName())
                    .exitCode(-1)
                    .errorType(errorType)
                    .build();
        } catch (Exception e) {
            log.error("SSH execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            log.debug("SSH execute exception details for node {}", node.getAlias(), e);
            return new ExecResult.Builder()
                    .stderr("SSH command execution failed: " + e.getClass().getSimpleName())
                    .exitCode(-1)
                    .errorType(ExecResult.ErrorType.UNKNOWN)
                    .build();
        } finally {
            if (session != null) session.disconnect();
        }
    }

    private ExecResult executeWithReuse(String credential, NodeEntity node, String command, ExecOptions options) {
        evictIdleSessions();
        String cacheKey = cacheKey(node);
        SessionHolder holder = sessionCache.computeIfAbsent(cacheKey, k -> new SessionHolder());

        try {
            synchronized (holder.lock) {
                Session session = ensureConnectedSession(holder, credential, node);
                holder.lastUsedAt = System.currentTimeMillis();
                ExecResult result = executeOverSession(session, command, options);
                holder.lastUsedAt = System.currentTimeMillis();
                return result;
            }
        } catch (InterruptedException e) {
            return new ExecResult.Builder()
                    .stderr("Execution interrupted by timeout")
                    .exitCode(-1)
                    .timedOut(true)
                    .errorType(ExecResult.ErrorType.TIMEOUT)
                    .build();
        } catch (JSchException e) {
            invalidateSession(cacheKey, holder);
            log.error("SSH execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            log.debug("SSH execute exception details for node {}", node.getAlias(), e);
            ExecResult.ErrorType errorType = mapJSchException(e);
            return new ExecResult.Builder()
                    .stderr("SSH execution failed: " + e.getClass().getSimpleName())
                    .exitCode(-1)
                    .errorType(errorType)
                    .build();
        } catch (Exception e) {
            log.error("SSH execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            log.debug("SSH execute exception details for node {}", node.getAlias(), e);
            return new ExecResult.Builder()
                    .stderr("SSH command execution failed: " + e.getClass().getSimpleName())
                    .exitCode(-1)
                    .errorType(ExecResult.ErrorType.UNKNOWN)
                    .build();
        }
    }

    private ExecResult executeOverSession(Session session, String command, ExecOptions options) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(options.getMaxOutputBytes());
            LimitedByteArrayOutputStream stderr = new LimitedByteArrayOutputStream(options.getMaxOutputBytes() / 4);

            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();

            channel.connect(resolveConnectTimeoutMs());

            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while (true) {
                while (in.available() > 0) {
                    len = in.read(buf);
                    if (len < 0) break;
                    stdout.write(buf, 0, len);
                }
                while (err.available() > 0) {
                    len = err.read(buf);
                    if (len < 0) break;
                    stderr.write(buf, 0, len);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0 || err.available() > 0) continue;
                    break;
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Execution interrupted by timeout");
                }

                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            boolean truncated = stdout.isTruncated() || stderr.isTruncated();
            long originalLength = stdout.getOriginalLength() + stderr.getOriginalLength();

            return new ExecResult.Builder()
                    .stdout(stdout.toString(StandardCharsets.UTF_8))
                    .stderr(stderr.toString(StandardCharsets.UTF_8))
                    .exitCode(exitCode)
                    .truncated(truncated)
                    .originalLength(originalLength)
                    .timedOut(false)
                    .build();
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private Session ensureConnectedSession(SessionHolder holder, String credential, NodeEntity node) throws JSchException {
        String fingerprint = buildFingerprint(node, credential);
        int timeoutMs = resolveConnectTimeoutMs();

        if (holder.session != null) {
            boolean expired = System.currentTimeMillis() - holder.lastUsedAt > resolveIdleTtlMs();
            boolean fingerprintChanged = holder.fingerprint == null || !holder.fingerprint.equals(fingerprint);
            if (!holder.session.isConnected() || expired || fingerprintChanged) {
                closeSession(holder.session);
                holder.session = null;
                holder.fingerprint = null;
            }
        }

        if (holder.session == null) {
            Session session = createSession(credential, node, timeoutMs);
            session.connect(timeoutMs);
            holder.session = session;
            holder.fingerprint = fingerprint;
            holder.lastUsedAt = System.currentTimeMillis();
        }

        return holder.session;
    }

    private void evictIdleSessions() {
        long now = System.currentTimeMillis();
        long ttlMs = resolveIdleTtlMs();
        for (Map.Entry<String, SessionHolder> entry : sessionCache.entrySet()) {
            SessionHolder holder = entry.getValue();
            synchronized (holder.lock) {
                if (holder.session == null) {
                    sessionCache.remove(entry.getKey(), holder);
                    continue;
                }
                if (now - holder.lastUsedAt > ttlMs) {
                    closeSession(holder.session);
                    holder.session = null;
                    holder.fingerprint = null;
                    sessionCache.remove(entry.getKey(), holder);
                }
            }
        }
    }

    private void invalidateSession(String cacheKey, SessionHolder holder) {
        synchronized (holder.lock) {
            closeSession(holder.session);
            holder.session = null;
            holder.fingerprint = null;
        }
        sessionCache.remove(cacheKey, holder);
    }

    private void closeSession(Session session) {
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignore) {
            }
        }
    }

    private int resolveConnectTimeoutMs() {
        int sec = properties.getSshConnectTimeoutSeconds();
        if (sec <= 0) {
            return DEFAULT_SSH_CONNECT_TIMEOUT_MS;
        }
        return sec * 1000;
    }

    private long resolveIdleTtlMs() {
        int sec = properties.getSshSessionIdleSeconds();
        if (sec <= 0) {
            sec = DEFAULT_SSH_IDLE_SECONDS;
        }
        return sec * 1000L;
    }

    private boolean isConnectionReuseEnabled() {
        return properties.isSshConnectionReuseEnabled();
    }

    private String cacheKey(NodeEntity node) {
        if (node.getId() != null && !node.getId().isBlank()) {
            return node.getId();
        }
        if (node.getAlias() != null && !node.getAlias().isBlank()) {
            return node.getAlias();
        }
        return (node.getHost() != null ? node.getHost() : "unknown-host") + ":" +
                (node.getPort() != null ? node.getPort() : 22) + ":" +
                (node.getUsername() != null ? node.getUsername() : "unknown-user");
    }

    private String buildFingerprint(NodeEntity node, String credential) {
        String raw = (node.getHost() != null ? node.getHost() : "") + "|" +
                (node.getPort() != null ? node.getPort() : "") + "|" +
                (node.getUsername() != null ? node.getUsername() : "") + "|" +
                (node.getAuthType() != null ? node.getAuthType() : "password") + "|" +
                (credential != null ? credential : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return raw;
        }
    }

    @PreDestroy
    void shutdown() {
        for (SessionHolder holder : sessionCache.values()) {
            synchronized (holder.lock) {
                closeSession(holder.session);
                holder.session = null;
                holder.fingerprint = null;
            }
        }
        sessionCache.clear();
        executor.shutdownNow();
    }

    private static class SessionHolder {
        private final Object lock = new Object();
        private Session session;
        private String fingerprint;
        private long lastUsedAt;
    }

    /**
     * 映射 JSch 异常到 ErrorType
     */
    private ExecResult.ErrorType mapJSchException(com.jcraft.jsch.JSchException e) {
        String message = e.getMessage();
        if (message == null) {
            return ExecResult.ErrorType.UNKNOWN;
        }

        message = message.toLowerCase(Locale.ROOT);
        if (message.contains("invalid privatekey")
                || message.contains("private key")
                || message.contains("passphrase")
                || message.contains("encrypted")) {
            return ExecResult.ErrorType.VALIDATION_ERROR;
        }
        if (message.contains("auth") || message.contains("password")) {
            return ExecResult.ErrorType.AUTHENTICATION_FAILED;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return ExecResult.ErrorType.NETWORK_ERROR;
        }
        if (message.contains("connection") || message.contains("connect")) {
            return ExecResult.ErrorType.NETWORK_ERROR;
        }
        if (message.contains("permission") || message.contains("denied")) {
            return ExecResult.ErrorType.PERMISSION_DENIED;
        }

        return ExecResult.ErrorType.UNKNOWN;
    }

    @Override
    public boolean testConnection(String credential, NodeEntity node) {
        return testConnectionWithDetails(credential, node).success();
    }

    @Override
    public ConnectionTestOutcome testConnectionWithDetails(String credential, NodeEntity node) {
        Session session = null;
        try {
            int timeoutMs = resolveConnectTimeoutMs();
            session = createSession(credential, node, timeoutMs);
            session.connect(timeoutMs);
            return session.isConnected()
                    ? ConnectionTestOutcome.ok()
                    : ConnectionTestOutcome.fail(ExecResult.ErrorType.NETWORK_ERROR, "SSH connection was not established");
        } catch (IllegalArgumentException e) {
            log.debug("SSH test connection invalid config for node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            return ConnectionTestOutcome.fail(ExecResult.ErrorType.VALIDATION_ERROR, e.getMessage());
        } catch (com.jcraft.jsch.JSchException e) {
            log.debug("SSH test connection failed for node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            ExecResult.ErrorType errorType = mapJSchException(e);
            return ConnectionTestOutcome.fail(errorType, mapJSchMessage(errorType, e.getMessage(), node.getAuthType()));
        } catch (Exception e) {
            log.debug("SSH test connection failed for node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            return ConnectionTestOutcome.fail(ExecResult.ErrorType.UNKNOWN, "SSH connection test failed");
        } finally {
            if (session != null) session.disconnect();
        }
    }

    private String mapJSchMessage(ExecResult.ErrorType errorType, String rawMessage, String authType) {
        String message = rawMessage != null ? rawMessage.toLowerCase(Locale.ROOT) : "";
        boolean keyAuth = "key".equalsIgnoreCase(authType);

        return switch (errorType) {
            case VALIDATION_ERROR -> keyAuth
                    ? "SSH private key is invalid, encrypted, or unsupported. Use unencrypted OpenSSH/PEM private key content."
                    : "SSH connection configuration is invalid";
            case AUTHENTICATION_FAILED -> keyAuth
                    ? "SSH key authentication failed. Check username, key pair, and ensure server has the matching public key."
                    : "SSH authentication failed";
            case NETWORK_ERROR -> {
                if (message.contains("connection refused")) {
                    yield "SSH connection refused. Check host/port and sshd service status.";
                }
                if (message.contains("timeout") || message.contains("timed out")) {
                    yield "SSH connection timed out. Check network reachability and firewall rules.";
                }
                yield "SSH network connection failed";
            }
            case PERMISSION_DENIED -> "SSH permission denied";
            default -> "SSH connection failed";
        };
    }

    private Session createSession(String credential, NodeEntity node, int timeoutMs) throws JSchException {
        JSch jsch = new JSch();

        String authType = node.getAuthType() != null ? node.getAuthType() : "password";

        if ("key".equals(authType)) {
            // 凭据是私钥内容（明确校验，避免返回模糊错误）
            if (credential == null || credential.isBlank()) {
                throw new IllegalArgumentException("SSH private key content is required");
            }
            if (!looksLikePrivateKey(credential)) {
                throw new IllegalArgumentException(
                        "SSH private key format is invalid. Paste the full private key content including BEGIN/END lines");
            }
            jsch.addIdentity("node-" + node.getAlias(), credential.getBytes(StandardCharsets.UTF_8), null, null);
        }

        // 移除默认值：如果配置缺失，让它失败（而不是使用危险的默认值）
        if (node.getHost() == null || node.getHost().isBlank()) {
            throw new IllegalArgumentException("SSH host is required (cannot be null or empty)");
        }
        if (node.getPort() == null) {
            throw new IllegalArgumentException("SSH port is required (cannot be null)");
        }
        if (node.getUsername() == null || node.getUsername().isBlank()) {
            throw new IllegalArgumentException("SSH username is required (cannot be null or empty)");
        }

        String host = node.getHost();
        int port = node.getPort();
        String username = node.getUsername();

        Session session = jsch.getSession(username, host, port);

        if ("password".equals(authType)) {
            session.setPassword(credential);
        }

        // 禁用严格主机密钥检查（运维场景）
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(timeoutMs);

        return session;
    }

    private boolean looksLikePrivateKey(String credential) {
        if (credential == null) {
            return false;
        }
        String normalized = credential.trim();
        return normalized.contains("BEGIN ") && normalized.contains("PRIVATE KEY")
                && normalized.contains("END ") && normalized.contains("PRIVATE KEY");
    }
}
