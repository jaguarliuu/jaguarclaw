package com.jaguarliu.ai.gateway.rpc.handler.node;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class NodeImportTemplateHandler implements RpcHandler {

    private static final String TEMPLATE =
            "alias,displayName,host,port,username,tags,safetyPolicy\n"
                    + "web-01,Web Server 01,192.168.1.10,22,deploy,prod|web,standard\n"
                    + "web-02,,192.168.1.11,22,deploy,,standard";

    @Override
    public String getMethod() {
        return "nodes.import.template";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> RpcResponse.success(request.getId(), Map.of("csv", TEMPLATE)));
    }
}
