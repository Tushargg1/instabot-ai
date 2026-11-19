package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.ManualAction;
import com.example.instagramreelai.entity.ManualActionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManualActionRepository extends JpaRepository<ManualAction, UUID> {

    Page<ManualAction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ManualAction> findByStatusOrderByCreatedAtDesc(ManualActionStatus status, Pageable pageable);

    List<ManualAction> findByResourceIdAndStatusInOrderByCreatedAtAsc(
            UUID resourceId, Collection<ManualActionStatus> statuses);

    long countByStatus(ManualActionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ManualAction a WHERE a.id = :id")
    Optional<ManualAction> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ManualAction a
               SET a.status = :claimedStatus,
                   a.claimedBy = :claimant,
                   a.claimedAt = :now,
                   a.leaseExpiresAt = :leaseExpiresAt,
                   a.completedAt = NULL,
                   a.failedAt = NULL,
                   a.failureReason = NULL,
                   a.version = a.version + 1
             WHERE a.id = :id
               AND (a.status = :pendingStatus
                    OR (a.status = :claimedStatus
                        AND (a.leaseExpiresAt IS NULL OR a.leaseExpiresAt <= :now)))
            """)
    int claim(@Param("id") UUID id,
              @Param("claimant") AppUser claimant,
              @Param("now") Instant now,
              @Param("leaseExpiresAt") Instant leaseExpiresAt,
              @Param("pendingStatus") ManualActionStatus pendingStatus,
              @Param("claimedStatus") ManualActionStatus claimedStatus);
}
