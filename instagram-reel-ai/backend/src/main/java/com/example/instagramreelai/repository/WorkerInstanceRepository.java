package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.WorkerInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerInstanceRepository extends JpaRepository<WorkerInstance, UUID> {
    Optional<WorkerInstance> findByInstanceId(String instanceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkerInstance w SET w.lastHeartbeatAt = :now " +
            "WHERE w.instanceId = :instanceId AND w.stoppedAt IS NULL")
    int heartbeat(@Param("instanceId") String instanceId, @Param("now") Instant now);

    @Query("SELECT w FROM WorkerInstance w WHERE w.stoppedAt IS NULL " +
            "AND w.lastHeartbeatAt >= :cutoff ORDER BY w.lastHeartbeatAt DESC")
    List<WorkerInstance> findActive(@Param("cutoff") Instant cutoff);

    @Query("SELECT w FROM WorkerInstance w WHERE w.stoppedAt IS NOT NULL " +
            "OR w.lastHeartbeatAt IS NULL OR w.lastHeartbeatAt < :cutoff " +
            "ORDER BY w.lastHeartbeatAt DESC")
    List<WorkerInstance> findStale(@Param("cutoff") Instant cutoff);
}