package com.jaguarliu.ai.storage.repository;

import com.jaguarliu.ai.storage.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<MessageEntity> findBySessionIdAndOwnerPrincipalIdOrderByCreatedAtAsc(String sessionId, String ownerPrincipalId);

    List<MessageEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    List<MessageEntity> findByRunIdAndOwnerPrincipalIdOrderByCreatedAtAsc(String runId, String ownerPrincipalId);

    void deleteBySessionId(String sessionId);
}
