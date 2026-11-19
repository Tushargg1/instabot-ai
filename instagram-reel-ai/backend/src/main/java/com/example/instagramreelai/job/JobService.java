package com.example.instagramreelai.job;

import com.example.instagramreelai.entity.ProcessingJob;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.repository.ProcessingJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class JobService {
    public static final List<String> ACTIVE_STATUSES = List.of(
            JobStatus.QUEUED, JobStatus.CLAIMED, JobStatus.DOWNLOADING,
            JobStatus.EXTRACTING_AUDIO, JobStatus.TRANSCRIBING,
            JobStatus.SUMMARIZING, JobStatus.SAVING, JobStatus.REPLYING);
    public static final Set<String> TERMINAL_RETRYABLE_STATUSES = Set.of(
            JobStatus.FAILED, JobStatus.MEDIA_UNAVAILABLE, JobStatus.TRANSCRIPTION_FAILED,
            JobStatus.SUMMARY_FAILED, JobStatus.REPLY_FAILED);

    private final ProcessingJobRepository jobRepository;
    private final long leaseSeconds;
    private final int pollBatchSize;

    public JobService(ProcessingJobRepository jobRepository,
                      @Value("${app.worker.job-lease-seconds:120}") long leaseSeconds,
                      @Value("${app.worker.poll-batch-size:10}") int pollBatchSize) {
        this.jobRepository = jobRepository;
        this.leaseSeconds = Math.max(30, leaseSeconds);
        this.pollBatchSize = Math.max(1, Math.min(100, pollBatchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingJob createJobIfAbsent(Reel reel, JobType jobType) {
        Optional<ProcessingJob> existing = jobRepository
                .findFirstByReelIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                        reel.getId(), jobType.name(), ACTIVE_STATUSES);
        if (existing.isPresent()) return existing.get();

        return jobRepository.saveAndFlush(ProcessingJob.builder()
                .reel(reel).user(reel.getUser()).jobType(jobType.name())
                .priority(jobType == JobType.REPLY ? 10 : 100)
                .status(JobStatus.QUEUED).stage(JobStatus.QUEUED)
                .attempts(0).maxAttempts(3).retryable(true).build());
    }

    /** Compatibility boundary for existing callers; invalid new job types are rejected. */
    public ProcessingJob createJobIfAbsent(Reel reel, String jobType) {
        return createJobIfAbsent(reel, JobType.fromPersisted(jobType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job type: " + jobType)));
    }

    @Transactional
    public boolean claimJob(UUID jobId, String workerId) {
        Instant now = Instant.now();
        return jobRepository.claim(jobId, workerId, now, now.plusSeconds(leaseSeconds)) == 1;
    }

    @Transactional
    public void updateStatus(UUID jobId, String workerId, String status, String stage) {
        Instant now = Instant.now();
        if (jobRepository.updateOwnedStatus(jobId, workerId, status, stage,
                now, now.plusSeconds(leaseSeconds)) != 1) {
            throw new IllegalStateException("Job lease was lost: " + jobId);
        }
        if (JobStatus.COMPLETED.equals(status)) finish(jobId, workerId, status, false, null, null);
    }

    @Transactional
    public boolean renewLease(UUID jobId, String workerId) {
        Instant now = Instant.now();
        return jobRepository.renewLease(jobId, workerId, now, now.plusSeconds(leaseSeconds)) == 1;
    }

    @Transactional
    public void markFailed(UUID jobId, String workerId, String status,
                           String errorCode, String errorMessage, boolean retryable) {
        ProcessingJob job = owned(jobId, workerId);
        job.setStatus(status);
        job.setStage(status);
        job.setLastErrorCode(errorCode);
        job.setLastErrorMessage(errorMessage);
        job.setRetryable(retryable);
        job.setRetryReason(retryable ? errorCode : null);
        job.setCompletedAt(Instant.now());
        job.setNextRetryAt(retryable && job.getAttempts() < job.getMaxAttempts()
                ? Instant.now().plus((long) Math.pow(2, job.getAttempts()) * 30, ChronoUnit.SECONDS)
                : null);
        clearLease(job);
        jobRepository.save(job);
    }

    @Transactional
    public void markUnknownType(UUID jobId, String workerId, String rawType) {
        markFailed(jobId, workerId, JobStatus.UNKNOWN_JOB_TYPE, "UNKNOWN_JOB_TYPE",
                "Unknown persisted job type: " + rawType, false);
    }

    @Transactional
    public void prepareAutomaticRetriesAndRecoverStaleClaims() {
        Instant now = Instant.now();
        jobRepository.findDueTerminalRetries(now, PageRequest.of(0, pollBatchSize)).forEach(this::requeue);
        jobRepository.findExpiredLeases(now, PageRequest.of(0, pollBatchSize)).forEach(job -> {
            if (job.getAttempts() < job.getMaxAttempts()) {
                job.setStatus(JobStatus.QUEUED);
                job.setStage("RECOVERED_STALE_LEASE");
                job.setRetryable(true);
                job.setRetryReason("STALE_LEASE");
                job.setNextRetryAt(now);
                clearLease(job);
                jobRepository.save(job);
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setStage(JobStatus.FAILED);
                job.setRetryable(false);
                job.setLastErrorCode("STALE_LEASE_EXHAUSTED");
                job.setCompletedAt(now);
                clearLease(job);
                jobRepository.save(job);
            }
        });
    }

    public List<ProcessingJob> findJobsReadyForProcessing() {
        return jobRepository.findJobsReadyForProcessing(Instant.now(),
                PageRequest.of(0, pollBatchSize));
    }

    private ProcessingJob owned(UUID jobId, String workerId) {
        ProcessingJob job = jobRepository.findById(jobId).orElseThrow();
        if (!workerId.equals(job.getLeaseOwner()) || job.getLeaseExpiresAt() == null
                || !job.getLeaseExpiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException("Job lease was lost: " + jobId);
        }
        return job;
    }

    private void finish(UUID jobId, String workerId, String status, boolean retryable,
                        String code, String message) {
        ProcessingJob job = owned(jobId, workerId);
        job.setStatus(status); job.setStage(status); job.setRetryable(retryable);
        job.setLastErrorCode(code); job.setLastErrorMessage(message);
        job.setCompletedAt(Instant.now()); job.setNextRetryAt(null);
        clearLease(job); jobRepository.save(job);
    }

    private void requeue(ProcessingJob job) {
        if (!TERMINAL_RETRYABLE_STATUSES.contains(job.getStatus()) || !Boolean.TRUE.equals(job.getRetryable())) return;
        job.setStatus(JobStatus.QUEUED); job.setStage(JobStatus.QUEUED);
        job.setCompletedAt(null); job.setNextRetryAt(null); clearLease(job);
        jobRepository.save(job);
    }

    private void clearLease(ProcessingJob job) {
        job.setLeaseOwner(null); job.setLeaseExpiresAt(null);
        job.setHeartbeatAt(null); job.setAssignedWorker(null);
    }
}
