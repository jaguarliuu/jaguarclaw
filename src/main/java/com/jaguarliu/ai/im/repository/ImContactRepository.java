package com.jaguarliu.ai.im.repository;

import com.jaguarliu.ai.im.entity.ImContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImContactRepository extends JpaRepository<ImContactEntity, String> {
    List<ImContactEntity> findByStatus(String status);
}
