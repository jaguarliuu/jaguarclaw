package com.jaguarliu.ai.im.repository;

import com.jaguarliu.ai.im.entity.ImMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImMessageRepository extends JpaRepository<ImMessageEntity, String> {
    // Secondary sort by id ensures stable ordering when two messages share the same millisecond timestamp
    List<ImMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(String conversationId);
}
