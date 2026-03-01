package com.jaguarliu.ai.agents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent profile 持久化实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_profile")
public class AgentProfileEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "workspace_path", nullable = false, columnDefinition = "TEXT")
    private String workspacePath;

    @Column(length = 120)
    private String model;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "allowed_tools", columnDefinition = "TEXT")
    private String allowedTools;

    @Column(name = "excluded_tools", columnDefinition = "TEXT")
    private String excludedTools;

    @Column(name = "heartbeat_interval")
    private Integer heartbeatInterval;

    @Column(name = "heartbeat_active_hours", length = 64)
    private String heartbeatActiveHours;

    @Column(name = "daily_token_limit")
    private Integer dailyTokenLimit;

    @Column(name = "monthly_cost_limit")
    private Double monthlyCostLimit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (enabled == null) {
            enabled = true;
        }
        if (isDefault == null) {
            isDefault = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

