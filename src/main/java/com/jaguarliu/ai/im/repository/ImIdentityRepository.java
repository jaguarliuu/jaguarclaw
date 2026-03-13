package com.jaguarliu.ai.im.repository;

import com.jaguarliu.ai.im.entity.ImIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImIdentityRepository extends JpaRepository<ImIdentityEntity, String> {}
