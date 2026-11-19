package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.*;
import com.example.instagramreelai.job.JobStatus;
import com.example.instagramreelai.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReelIngestionService {
    private static final List<String> ACTIVE_JOB_STATUSES = List.of(
            JobStatus.QUEUED, JobStatus.CLAIMED, JobStatus.DOWNLOADING,
            JobStatus.EXTRACTING_AUDIO, JobStatus.TRANSCRIBING,
            JobStatus.SUMMARIZING, JobStatus.SAVING, JobStatus.REPLYING);

    private final WebhookEventRepository eventRepository;
    private final ReelRepository reelRepository;
    private final ReelRequestRepository requestRepository;
    private final ProcessingJobRepository jobRepository;

    @Transactional
    public IngestionResult recordRequest(String messageId, String senderId, String recipientId,
                                         String rawPayload, Reel reel) {
        var existingEvent = eventRepository.findByExternalEventId(messageId);
        if (existingEvent.isPresent()) {
            return new IngestionResult(reel, true, false);
        }

        InstagramWebhookEvent event = eventRepository.save(InstagramWebhookEvent.builder()
                .externalEventId(messageId)
                .eventType("message")
                .senderId(senderId)
                .recipientId(recipientId)
                .user(reel.getUser())
                .rawPayload(rawPayload)
                .build());

        requestRepository.save(ReelRequest.builder()
                .reel(reel)
                .webhookEvent(event)
                .instagramSenderId(senderId)
                .instagramRecipientId(recipientId)
                .build());

        reel.setRequestCount((reel.getRequestCount() == null ? 0L : reel.getRequestCount()) + 1);
        reel.setLastRequestedAt(Instant.now());
        if (reel.getWebhookEvent() == null) reel.setWebhookEvent(event);
        reelRepository.save(reel);

        event.setProcessed(true);
        eventRepository.save(event);

        boolean cached = JobStatus.COMPLETED.equals(reel.getProcessingStatus());
        String jobType = cached ? "REPLY" : "AI_PIPELINE";
        boolean jobCreated = false;
        if (jobRepository.findFirstByReelIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
                reel.getId(), jobType, ACTIVE_JOB_STATUSES).isEmpty()) {
            jobRepository.save(ProcessingJob.builder()
                    .reel(reel)
                    .user(reel.getUser())
                    .jobType(jobType)
                    .priority(cached ? 10 : 100)
                    .status(JobStatus.QUEUED)
                    .stage(JobStatus.QUEUED)
                    .attempts(0)
                    .maxAttempts(3)
                    .build());
            jobCreated = true;
        }
        return new IngestionResult(reel, false, jobCreated);
    }

    public record IngestionResult(Reel reel, boolean duplicateEvent, boolean jobCreated) {}
}
