package com.jaguarliu.ai.nodeconsole;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Node Console 子系统配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "node-console")
public class NodeConsoleProperties {

    /**
     * AES-256 加密密钥（64 字符 hex = 32 字节）
     */
    private String encryptionKey;

    /**
     * 默认安全策略：strict | standard | relaxed
     */
    private String defaultSafetyPolicy = "strict";

    /**
     * SSH 连接超时（秒）- 仅用于建立 SSH 连接阶段
     */
    private int sshConnectTimeoutSeconds = 10;

    /**
     * 是否启用 SSH 连接复用
     */
    private boolean sshConnectionReuseEnabled = true;

    /**
     * SSH 连接空闲保活时间（秒）
     * 超过该时间未使用的连接会被回收
     */
    private int sshSessionIdleSeconds = 300;

    /**
     * 命令执行超时（秒）- 包含命令运行和输出读取的总时间
     */
    private int execTimeoutSeconds = 60;

    /**
     * 最大输出长度（字节）- 防止 OOM
     */
    private int maxOutputBytes = 32000;

    /**
     * SSH 严格主机密钥检查（StrictHostKeyChecking）
     * 默认 false（向后兼容）。生产运维场景建议开启以防 MITM 攻击。
     * true  → StrictHostKeyChecking=yes（拒绝未知主机）
     * false → StrictHostKeyChecking=no（自动接受）
     */
    private boolean sshStrictHostKeyChecking = false;
}
