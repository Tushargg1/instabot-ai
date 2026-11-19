package com.example.instagramreelai.job;

import com.example.instagramreelai.ai.SummaryService;
import com.example.instagramreelai.ai.TranscriptionService;
import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.example.instagramreelai.entity.*;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.media.MediaService;
import com.example.instagramreelai.repository.*;
import com.example.instagramreelai.service.EncryptionService;
import com.example.instagramreelai.service.ReelCacheService;
import com.example.instagramreelai.service.ReelResultService;
import com.example.instagramreelai.service.ResourceDetectionService;
import com.example.instagramreelai.service.WorkerRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobService jobService;
    private final ProcessingJobRepository jobRepository;
    private final ReelRepository reelRepository;
    private final ReelSummaryRepository summaryRepository;
    private final ReelRequestRepository requestRepository;
    private final ReelResultService resultService;
    private final ReelCacheService cacheService;
    private final InstagramClient instagramClient;
    private final TranscriptionService transcriptionService;
    private final SummaryService summaryService;
    private final MediaService mediaService;
    private final AppProperties appProperties;
    private final EncryptionService encryptionService;
    private final ResourceDetectionService resourceDetectionService;
    private final WorkerRegistryService workerRegistryService;

    @Scheduled(fixedDelay = 5000)
    public void pollAndProcess() {
        jobService.prepareAutomaticRetriesAndRecoverStaleClaims();
        String workerId = workerRegistryService.getInstanceId();
        List<ProcessingJob> readyJobs = jobService.findJobsReadyForProcessing();
        for (ProcessingJob job : readyJobs) {
            if (jobService.claimJob(job.getId(), workerId)) processJob(job, workerId);
        }
    }

    void processJob(ProcessingJob job, String workerId) {
        log.info("Processing job={} type={} for reel={} worker={}",
                job.getId(), job.getJobType(), job.getReel().getId(), workerId);
        Optional<JobType> parsedType = JobType.fromPersisted(job.getJobType());
        if (parsedType.isEmpty()) {
            jobService.markUnknownType(job.getId(), workerId, job.getJobType());
            return;
        }
        switch (parsedType.get()) {
            case REPLY -> { processReplyJob(job, workerId); return; }
            case RESOURCE_DETECTION -> { processResourceJob(job, workerId, false); return; }
            case RESOURCE_REFRESH -> { processResourceJob(job, workerId, true); return; }
            case MANUAL_ACTION -> { processResourceJob(job, workerId, true); return; }
            case AI_PIPELINE -> { /* continue below */ }
        }

        Path mediaFile = null;
        Path audioFile = null;

        try {
            Reel reel = reelRepository.findByIdWithRelations(job.getReel().getId())
                    .orElseThrow(() -> new IllegalStateException("Reel not found"));

            // Stage: DOWNLOADING
            jobService.updateStatus(job.getId(), workerId, JobStatus.DOWNLOADING, JobStatus.DOWNLOADING);
            InstagramAccount account = reel.getInstagramAccount();
            String accountId = account != null ? account.getInstagramAccountId()
                    : appProperties.getInstagram().getAccountId();
            String accessToken = account != null && account.getAccessTokenEncrypted() != null
                    ? encryptionService.decrypt(account.getAccessTokenEncrypted())
                    : appProperties.getInstagram().getAccessToken();
            Optional<String> mediaUrl = instagramClient.resolveMediaUrl(
                    reel.getExternalMediaId(), accessToken);

            if (mediaUrl.isEmpty()) {
                reel.setProcessingStatus(JobStatus.MEDIA_UNAVAILABLE);
                reelRepository.save(reel);
                jobService.markFailed(job.getId(), workerId, JobStatus.MEDIA_UNAVAILABLE,
                        "MEDIA_NOT_ACCESSIBLE",
                        "Instagram did not provide downloadable media for this content.", true);
                boolean delivered = fanOut(reel,
                        "Sorry, I couldn't access the media for this Reel. " +
                                "The content may not be available through the Instagram API. " +
                                "Please submit a supported Reel URL.", accountId, accessToken);
                if (!delivered) jobService.createJobIfAbsent(reel, "REPLY");
                return;
            }

            mediaFile = mediaService.downloadMedia(mediaUrl.get());

            // Stage: EXTRACTING_AUDIO
            jobService.updateStatus(job.getId(), workerId, JobStatus.EXTRACTING_AUDIO, JobStatus.EXTRACTING_AUDIO);
            audioFile = mediaService.extractAudio(mediaFile);

            // Stage: TRANSCRIBING
            jobService.updateStatus(job.getId(), workerId, JobStatus.TRANSCRIBING, JobStatus.TRANSCRIBING);
            TranscriptionService.TranscriptionResult transcription = transcriptionService.transcribe(audioFile);

            // Stage: SUMMARIZING
            jobService.updateStatus(job.getId(), workerId, JobStatus.SUMMARIZING, JobStatus.SUMMARIZING);
            AiSummaryResult summaryResult = summaryService.summarize(transcription.text());

            // Stage: SAVING (idempotent upsert)
            jobService.updateStatus(job.getId(), workerId, JobStatus.SAVING, JobStatus.SAVING);
            resultService.save(reel, transcription, summaryResult);
            resourceDetectionService.detectAndPersist(reel.getId());

            // Stage: REPLYING to every pending request for this canonical Reel.
            jobService.updateStatus(job.getId(), workerId, JobStatus.REPLYING, JobStatus.REPLYING);
            String replyMessage = formatReply(summaryResult);
            cacheService.putReply(reel.getId(), replyMessage);
            boolean deliveredToAll = fanOut(reel, replyMessage, accountId, accessToken);

            reel.setProcessingStatus(JobStatus.COMPLETED);
            reelRepository.save(reel);
            jobService.updateStatus(job.getId(), workerId, JobStatus.COMPLETED, JobStatus.COMPLETED);
            if (!deliveredToAll) jobService.createJobIfAbsent(reel, "REPLY");
            log.info("Job {} completed; canonical Reel result fanned out", job.getId());

        } catch (Exception e) {
            log.error("Job {} failed", job.getId(), e);
            jobService.markFailed(job.getId(), workerId, JobStatus.FAILED,
                    "PROCESSING_ERROR", e.getMessage(), true);
        } finally {
            mediaService.cleanup(mediaFile, audioFile);
        }
    }

    private void processReplyJob(ProcessingJob job, String workerId) {
        try {
            Reel reel = reelRepository.findByIdWithRelations(job.getReel().getId())
                    .orElseThrow(() -> new IllegalStateException("Reel not found"));
            ReelSummary summary = summaryRepository.findByReelId(reel.getId())
                    .orElseThrow(() -> new IllegalStateException("Completed Reel has no summary"));
            InstagramAccount account = reel.getInstagramAccount();
            String accountId = account != null ? account.getInstagramAccountId()
                    : appProperties.getInstagram().getAccountId();
            String accessToken = account != null && account.getAccessTokenEncrypted() != null
                    ? encryptionService.decrypt(account.getAccessTokenEncrypted())
                    : appProperties.getInstagram().getAccessToken();
            String replyMessage = cacheService.getReply(reel.getId())
                    .orElseGet(() -> formatReply(summary));
            cacheService.putReply(reel.getId(), replyMessage);
            if (fanOut(reel, replyMessage, accountId, accessToken)) {
                jobService.updateStatus(job.getId(), workerId, JobStatus.COMPLETED, JobStatus.COMPLETED);
            } else {
                jobService.markFailed(job.getId(), workerId, JobStatus.REPLY_FAILED,
                        "REPLY_FAILED", "One or more requester replies could not be delivered", true);
            }
        } catch (Exception exception) {
            log.error("Reply job {} failed", job.getId(), exception);
            jobService.markFailed(job.getId(), workerId, JobStatus.REPLY_FAILED,
                    "REPLY_FAILED", exception.getMessage(), true);
        }
    }

    private void processResourceJob(ProcessingJob job, String workerId, boolean refresh) {
        try {
            if (refresh) resourceDetectionService.refreshPreservingLastUseful(job.getReel().getId());
            else resourceDetectionService.detectAndPersist(job.getReel().getId());
            jobService.updateStatus(job.getId(), workerId, JobStatus.COMPLETED, JobStatus.COMPLETED);
        } catch (Exception exception) {
            log.error("Resource job {} failed", job.getId(), exception);
            jobService.markFailed(job.getId(), workerId, JobStatus.FAILED,
                    "RESOURCE_PROCESSING_ERROR", exception.getMessage(), true);
        }
    }

    private boolean fanOut(Reel reel, String message, String accountId, String accessToken) {
        List<ReelRequest> pending = requestRepository
                .findByReelIdAndReplyStatusAndInstagramSenderIdIsNotNullOrderByCreatedAtAsc(
                        reel.getId(), "PENDING");
        boolean deliveredToAll = true;
        for (ReelRequest request : pending) {
            boolean delivered = instagramClient.sendReply(
                    accountId, request.getInstagramSenderId(), message, accessToken);
            if (delivered) {
                request.setStatus("COMPLETED");
                request.setReplyStatus("REPLIED");
                request.setCompletedAt(Instant.now());
                request.setRepliedAt(Instant.now());
                request.setLastError(null);
            } else {
                deliveredToAll = false;
                request.setLastError("Instagram reply could not be delivered");
            }
            requestRepository.save(request);
        }
        return deliveredToAll;
    }

    private String formatReply(ReelSummary summary) {
        String title = summary.getTitle() == null ? "Reel summary" : summary.getTitle();
        return truncate("📝 " + title + "\n\n" + summary.getSummary());
    }

    private String formatReply(AiSummaryResult summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 ").append(summary.getTitle()).append("\n\n");
        sb.append(summary.getSummary()).append("\n\n");

        if (summary.getKeyPoints() != null && !summary.getKeyPoints().isEmpty()) {
            sb.append("🔑 Key Points:\n");
            for (String point : summary.getKeyPoints()) {
                sb.append("• ").append(point).append("\n");
            }
        }

        return truncate(sb.toString());
    }

    private String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 997) + "..." : message;
    }
}
