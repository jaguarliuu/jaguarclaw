package com.jaguarliu.ai.agents.repository;

import com.jaguarliu.ai.agents.entity.AgentProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentProfileRepository extends JpaRepository<AgentProfileEntity, String> {

    Optional<AgentProfileEntity> findByName(String name);

    Optional<AgentProfileEntity> findByIsDefaultTrue();

    List<AgentProfileEntity> findByEnabledTrueOrderByCreatedAtAsc();

    List<AgentProfileEntity> findAllByOrderByCreatedAtAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}
