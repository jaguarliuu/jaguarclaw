#!/bin/bash
# 每 10 分钟检查 Codex 状态并汇报

LOG_FILE="/tmp/codex-phase2-monitor.log"
TELEGRAM_CHAT="8514777800"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

check_codex() {
    # 检查 tmux session 是否存在
    if ! tmux has-session -t codex-phase2 2>/dev/null; then
        log "Codex session not found"
        return 1
    fi

    # 获取最近的输出
    OUTPUT=$(tmux capture-pane -t codex-phase2 -p -S -30 2>/dev/null)
    
    # 检查是否在等待审批
    if echo "$OUTPUT" | grep -q "Would you like to run\|Press enter to confirm"; then
        log "Codex waiting for approval"
        # 自动批准
        tmux send-keys -t codex-phase2 '1' && sleep 1 && tmux send-keys -t codex-phase2 Enter
        log "Auto-approved"
    fi
    
    # 检查是否完成
    if echo "$OUTPUT" | grep -q "tokens used\|Done\|completed"; then
        log "Codex completed a turn"
    fi
    
    return 0
}

send_report() {
    local status="$1"
    # 通过 OpenClaw 发送 Telegram 消息
    curl -s -X POST "http://localhost:3000/api/message/send" \
        -H "Content-Type: application/json" \
        -d "{\"chatId\":\"$TELEGRAM_CHAT\",\"message\":\"🔄 Codex 状态: $status\"}" \
        > /dev/null 2>&1 || true
}

# 主逻辑
log "=== Codex Monitor Start ==="

if check_codex; then
    # 获取最近的进度
    RECENT=$(tmux capture-pane -t codex-phase2 -p -S -5 2>/dev/null | tail -3)
    send_report "运行中\n\`\`\`\n$RECENT\n\`\`\`"
else
    send_report "已停止或未启动"
fi

log "=== Codex Monitor End ==="
