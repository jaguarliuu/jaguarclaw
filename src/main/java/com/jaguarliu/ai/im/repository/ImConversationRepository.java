package com.jaguarliu.ai.im.repository;

import com.jaguarliu.ai.im.entity.ImConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ImConversationRepository extends JpaRepository<ImConversationEntity, String> {
    @Query("SELECT c FROM ImConversationEntity c ORDER BY c.lastMsgAt DESC NULLS LAST")
    List<ImConversationEntity> findAllOrderByLastMsgAtDesc();
}
