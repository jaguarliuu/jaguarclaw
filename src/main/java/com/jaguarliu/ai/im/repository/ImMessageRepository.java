package com.jaguarliu.ai.im.repository;

import com.jaguarliu.ai.im.entity.ImMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImMessageRepository extends JpaRepository<ImMessageEntity, String> {
    List<ImMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
