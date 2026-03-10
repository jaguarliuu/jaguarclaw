---
name: server-health-check
description: >
  Inspect and report the health of one or more servers or nodes.
  Checks CPU, memory, disk, running services, network, processes,
  and recent error logs. Produces a structured, diff-aware report
  that highlights anomalies and trends rather than raw numbers.
  Use when the user asks to check server status, run a health inspection,
  monitor resources, audit nodes, or investigate performance issues.
tags:
  - ops
  - devops
  - monitoring
  - infrastructure
  - server
  - linux
  - health
  - inspection
triggers:
  - check server
  - inspect node
  - server health
  - health check
  - server status
  - resource usage
  - 巡检
  - 检查服务器
  - 节点状态
  - 资源监控
examples:
  - "check the health of prod-web-01"
  - "inspect all nodes and report anomalies"
  - "run a health check on the database server"
---

# Server Health Check

## Overview

This skill guides a structured health inspection of one or more servers.
The goal is **actionable signal, not raw data** — report anomalies, trends,
and issues rather than dumping all metrics.

Always produce a structured report at the end. Skip sections that return
no actionable findings (don't pad the report with "everything is fine" lines).

---

## Phase 1: Collect Raw Metrics

Run the following checks. Adapt commands if the node uses a non-standard OS
(e.g., Alpine, macOS, BSD). All commands should be non-destructive read-only.

### 1.1 System Load & Uptime
```bash
uptime
# Shows: load average (1m, 5m, 15m) and uptime
# Concern: load average > number of CPU cores
```

### 1.2 CPU
```bash
# Quick snapshot (non-interactive)
top -bn1 | head -20

# Number of CPUs (for context)
nproc
```

### 1.3 Memory & Swap
```bash
free -h
# Concern: available < 10% of total
# Concern: swap used > 50% of total swap
```

### 1.4 Disk Usage
```bash
df -h --output=source,size,used,avail,pcent,target | grep -v tmpfs | grep -v udev
# Concern: any filesystem > 80% full
# Critical: any filesystem > 90% full
```

### 1.5 Inode Usage
```bash
df -i | grep -v tmpfs | grep -v udev
# Concern: inode usage > 80% (can cause "no space" errors even with free disk space)
```

### 1.6 Top Processes by CPU and Memory
```bash
# Top 10 by CPU
ps aux --sort=-%cpu | head -11

# Top 10 by Memory
ps aux --sort=-%mem | head -11
```

### 1.7 Zombie Processes
```bash
ps aux | awk '$8 == "Z"'
# Any output here is a concern
```

### 1.8 Failed Systemd Services
```bash
systemctl list-units --state=failed --no-legend 2>/dev/null || echo "systemd not available"
```

### 1.9 Critical Service Status
Check services relevant to this node's role. Common ones:

```bash
# For web servers
systemctl is-active nginx 2>/dev/null || systemctl is-active apache2 2>/dev/null

# For databases
systemctl is-active mysql 2>/dev/null || systemctl is-active postgresql 2>/dev/null || systemctl is-active mongod 2>/dev/null

# For containers
systemctl is-active docker 2>/dev/null
docker ps --filter status=exited --filter status=dead 2>/dev/null | head -10

# General: list all running services
systemctl list-units --type=service --state=running --no-legend 2>/dev/null | head -20
```

### 1.10 Network
```bash
# Open listening ports
ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null

# Active connections count
ss -s 2>/dev/null

# Network interface errors
ip -s link 2>/dev/null | grep -A4 "errors\|dropped" | grep -v "^$"
```

### 1.11 Recent Error Logs
```bash
# System errors in the last hour
journalctl -p err -S "1 hour ago" --no-pager -n 50 2>/dev/null \
  || grep -i "error\|critical\|fail" /var/log/syslog 2>/dev/null | tail -30 \
  || grep -i "error\|critical\|fail" /var/log/messages 2>/dev/null | tail -30

# Kernel ring buffer
dmesg -T 2>/dev/null | grep -iE "error|fail|oom|killed" | tail -20
```

### 1.12 Last Login / Auth Failures (Security)
```bash
# Recent failed logins
lastb 2>/dev/null | head -10 || grep "Failed password" /var/log/auth.log 2>/dev/null | tail -10

# Who is currently logged in
who
```

---

## Phase 2: Anomaly Classification

After collecting raw data, classify findings:

| Severity | Criteria |
|----------|----------|
| 🔴 CRITICAL | Service down, disk > 90%, OOM killer fired, kernel panic |
| 🟡 WARNING  | Load > CPU count, disk 80–90%, swap > 50%, zombie procs, auth failures |
| 🔵 INFO     | Notable but not immediately actionable (e.g., high conn count, slow disk) |

Findings that are within normal bounds → **do not report**. Only report deviations.

---

## Phase 3: Trend Analysis (if Memory available)

If previous inspection data is available in memory, compare:

- Is disk usage trending up? Calculate rate: `(current - previous) / hours_elapsed`
- Is load average consistently elevated compared to historical baseline?
- Are new services failed that weren't before?

If disk is trending up, estimate time to full:
```
hours_to_full = free_gb / growth_rate_gb_per_hour
```

---

## Phase 4: Structured Report

Produce a report in this format. **Omit sections with no findings.**

```
## Health Report: {hostname} — {timestamp}

**Overall Status:** 🟢 Healthy / 🟡 Degraded / 🔴 Critical

### 🔴 Critical Issues
- [issue description, metric, recommended action]

### 🟡 Warnings
- [issue description, metric, recommended action]

### 🔵 Notable
- [informational findings]

### Trends
- Disk /data: 62% → 78% over 24h, estimated full in ~3 days
- Load average stable around 1.2 (4 cores, within normal range)

### Metrics Snapshot
| Resource | Value | Status |
|----------|-------|--------|
| CPU load (1m/5m/15m) | 0.8 / 1.2 / 1.0 | 🟢 |
| Memory used | 6.2 GB / 8 GB (77%) | 🟡 |
| Disk / | 45% | 🟢 |
| Disk /data | 78% | 🟡 |
| Swap used | 120 MB / 2 GB (6%) | 🟢 |
| Failed services | 0 | 🟢 |
```

---

## Multi-Node Inspection

When checking multiple nodes, use parallel sub-agents (one per node) and
aggregate results in a summary table:

```
## Fleet Health Summary — {timestamp}

| Node | Status | Critical | Warnings | Notable |
|------|--------|----------|----------|---------|
| prod-web-01 | 🟢 | — | disk 78% | — |
| prod-web-02 | 🔴 | nginx down | — | — |
| prod-db-01 | 🟡 | — | swap 60% | conn 420 |

### Nodes Requiring Immediate Action
1. prod-web-02: nginx is not running — check `systemctl status nginx`
```

---

## Remediation Guidance

For common findings, suggest (but do not automatically execute) remediation:

| Finding | Suggested Action |
|---------|-----------------|
| Disk > 80% | `du -sh /* 2>/dev/null \| sort -rh \| head -20` to find large dirs |
| Service down | `systemctl status <service>` + `journalctl -u <service> -n 50` |
| High memory | Identify top consumers with `ps aux --sort=-%mem \| head -10` |
| Zombie procs | Find parent: `ps -o ppid= -p <zombie_pid>` then restart parent |
| OOM events | `grep -i "oom\|killed" /var/log/syslog` for history |

**For CRITICAL issues:** Report immediately and ask if remediation should be attempted.
**For WARNINGS:** Include in report and suggest next steps.
**Do not auto-remediate without explicit user confirmation.**

---

## Notes

- Prefer non-interactive commands (`top -bn1` not `top`)
- If a command is unavailable, skip it and note "not available on this node"
- For Docker/Kubernetes environments, add container-level checks
- Adapt service names to the node's actual role (don't check nginx on a DB server)
