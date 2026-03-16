package com.jaguarliu.ai.schedule;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRunLogRepository extends JpaRepository<ScheduleRunLogEntity, String> {

    List<ScheduleRunLogEntity> findByTaskIdOrderByStartedAtDesc(String taskId, Pageable pageable);

    List<ScheduleRunLogEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
