package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    Page<ProcessingJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ProcessingJob> findByStatus(String status, Pageable pageable);
    long countByUserId(UUID userId);
    long countByUserIdAndStatus(UUID userId, String status);
    long countByStatus(String status);
    long countByReelUserId(UUID userId);
    long countByReelUserIdAndStatus(UUID userId, String status);

    @Query("SELECT j FROM ProcessingJob j JOIN FETCH j.reel WHERE j.status = 'QUEUED' " +
            "AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now) " +
            "ORDER BY j.priority ASC, j.createdAt ASC")
    List<ProcessingJob> findJobsReadyForProcessing(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT j FROM ProcessingJob j WHERE j.leaseOwner IS NOT NULL " +
            "AND j.leaseExpiresAt <= :now AND j.status NOT IN ('COMPLETED','FAILED','MEDIA_UNAVAILABLE','TRANSCRIPTION_FAILED','SUMMARY_FAILED','REPLY_FAILED','UNKNOWN_JOB_TYPE')")
    List<ProcessingJob> findExpiredLeases(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT j FROM ProcessingJob j WHERE j.retryable = true AND j.attempts < j.maxAttempts " +
            "AND j.status IN ('FAILED','MEDIA_UNAVAILABLE','TRANSCRIPTION_FAILED','SUMMARY_FAILED','REPLY_FAILED') " +
            "AND j.nextRetryAt IS NOT NULL AND j.nextRetryAt <= :now ORDER BY j.nextRetryAt ASC")
    List<ProcessingJob> findDueTerminalRetries(@Param("now") Instant now, Pageable pageable);

    Optional<ProcessingJob> findFirstByReelIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
            UUID reelId, String jobType, Collection<String> statuses);
    long countByReelIdAndJobType(UUID reelId, String jobType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProcessingJob j SET j.status = 'CLAIMED', j.stage = 'CLAIMED',
            j.startedAt = COALESCE(j.startedAt, :now), j.completedAt = NULL,
            j.attempts = j.attempts + 1, j.assignedWorker = :workerId,
            j.leaseOwner = :workerId, j.leaseExpiresAt = :leaseUntil, j.heartbeatAt = :now
        WHERE j.id = :jobId AND j.status = 'QUEUED'
          AND j.attempts < j.maxAttempts
          AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)
        """)
    int claim(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
              @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProcessingJob j SET j.heartbeatAt = :now, j.leaseExpiresAt = :leaseUntil
        WHERE j.id = :jobId AND j.leaseOwner = :workerId
          AND j.leaseExpiresAt > :now
        """)
    int renewLease(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
                   @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProcessingJob j SET j.status = :status, j.stage = :stage,
            j.heartbeatAt = :now, j.leaseExpiresAt = :leaseUntil
        WHERE j.id = :jobId AND j.leaseOwner = :workerId
          AND j.leaseExpiresAt > :now
        """)
    int updateOwnedStatus(@Param("jobId") UUID jobId, @Param("workerId") String workerId,
                          @Param("status") String status, @Param("stage") String stage,
                          @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
