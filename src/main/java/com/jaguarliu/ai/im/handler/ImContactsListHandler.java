package com.jaguarliu.ai.im.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.im.dto.ImContactDto;
import com.jaguarliu.ai.im.entity.ImContactEntity;
import com.jaguarliu.ai.im.repository.ImContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ImContactsListHandler implements RpcHandler {

    private final ImContactRepository contactRepo;

    @Override
    public String getMethod() { return "im.contacts.list"; }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            List<ImContactDto> contacts = contactRepo.findByStatus("active").stream()
                .map(this::toDto)
                .toList();
            return RpcResponse.success(request.getId(), contacts);
        });
    }

    private ImContactDto toDto(ImContactEntity e) {
        return ImContactDto.builder()
            .nodeId(e.getNodeId())
            .displayName(e.getDisplayName())
            .pairedAt(e.getPairedAt().toString())
            .status(e.getStatus())
            .avatarStyle(e.getAvatarStyle())
            .avatarSeed(e.getAvatarSeed())
            .build();
    }
}
