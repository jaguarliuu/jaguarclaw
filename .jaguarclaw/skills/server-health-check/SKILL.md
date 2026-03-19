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

This skill performs a structured, read-only health inspection for one or more
servers and always produces an HTML inspection report.

Primary goal: surface actionable anomalies, trends, and next steps rather than
dumping raw command output.

## Required Output Contract

Every run of this skill must produce an HTML report file with `write_file`.

- Single node default path: `reports/server-health-<hostname>-<YYYYMMDD-HHMMSS>.html`
- Multi-node default path: `reports/server-health-fleet-<YYYYMMDD-HHMMSS>.html`
- The final user-facing response must include:
  - a short findings summary
  - the exact generated report path
  - the selected template style
- Do not end with only Markdown. The HTML file is required.

If the user does not specify a style, choose `light`.

## Template Selection

Use one of these built-in HTML templates and replace the placeholders:

- `assets/report-light.html`
  - default, clean dashboard, best for normal review
- `assets/report-dark.html`
  - use when the user asks for dark/night/console style
- `assets/report-paper.html`
  - use when the user wants a printable, formal, memo-like report

Fill placeholders such as:

- `{{REPORT_TITLE}}`
- `{{REPORT_SUBTITLE}}`
- `{{OVERALL_STATUS}}`
- `{{NODE_SCOPE}}`
- `{{CRITICAL_WARNING_COUNTS}}`
- `{{GENERATED_AT}}`
- `{{EXECUTIVE_SUMMARY}}`
- `{{METRIC_CARDS}}`
- `{{NODE_TABLE_ROWS}}`
- `{{TREND_ITEMS}}`
- `{{CRITICAL_ITEMS}}`
- `{{WARNING_ITEMS}}`
- `{{INFO_ITEMS}}`
- `{{NEXT_STEP_ITEMS}}`
- `{{REPORT_PATH}}`

When filling repeated sections, generate HTML fragments, for example:

- metric card:
  - light/dark: `<div class="kpi"><span class="label">CPU load</span><div class="value">1.2 / 0.9 / 0.8</div></div>`
  - paper: `<div class="metric-card"><span class="label">CPU load</span><div class="value">1.2 / 0.9 / 0.8</div></div>`
- issue item:
  - light/dark: `<article class="issue"><span class="pill bad">CRITICAL</span><h3>nginx down</h3><p>systemctl is-active nginx returned inactive. Check service logs.</p></article>`
  - dark alternative class name is `item`
  - paper: `<article class="finding"><span class="tag bad">CRITICAL</span><h3>nginx down</h3><p>systemctl is-active nginx returned inactive. Check service logs.</p></article>`

If a section has no content, insert a single neutral placeholder item instead of
leaving a raw token behind, for example:

- `No critical findings in this inspection window.`
- `No trend baseline available for this node yet.`

## Workflow

1. Collect health metrics with read-only commands.
2. Classify anomalies by severity.
3. Compare against prior context if historical data is available.
4. Build the HTML report from one of the built-in templates.
5. Save the HTML report with `write_file`.
6. Tell the user where the report was written.

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

- `CRITICAL`: service down, disk > 90%, OOM killer fired, kernel panic
- `WARNING`: load > CPU count, disk 80-90%, swap > 50%, zombie procs, auth failures
- `INFO`: notable but not immediately actionable

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

## HTML Report Rules

The HTML report should include these sections:

- executive summary
- overall health status
- metrics snapshot
- node or fleet summary table
- critical issues
- warnings
- notable information
- trend notes
- recommended next steps

Do not dump raw terminal output blocks into the final report. Convert them into:

- concise metric summaries
- anomaly statements
- remediation suggestions

Keep the HTML self-contained:

- no remote CSS or JS
- no external images
- inline styles only
- printable in a browser without extra assets

---

## Multi-Node Inspection

When checking multiple nodes, use parallel sub-agents where available, then
aggregate all nodes into a single fleet HTML report. Include:

- a fleet summary table
- a per-node status row
- a short "nodes requiring immediate action" section in the HTML

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
