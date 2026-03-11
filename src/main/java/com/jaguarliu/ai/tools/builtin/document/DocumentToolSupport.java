package com.jaguarliu.ai.tools.builtin.document;

import com.jaguarliu.ai.gateway.ws.ConnectionManager;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import lombok.experimental.UtilityClass;

@UtilityClass
class DocumentToolSupport {
    static String resolveOwnerId(ToolExecutionContext ctx, ConnectionManager connectionManager) {
        if (ctx == null || ctx.getConnectionId() == null) return "local-default";
        var principal = connectionManager.getPrincipal(ctx.getConnectionId());
        return principal != null ? principal.getPrincipalId() : "local-default";
    }
}
