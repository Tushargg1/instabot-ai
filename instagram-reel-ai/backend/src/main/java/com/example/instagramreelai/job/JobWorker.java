package com.example.instagramreelai.job;

import com.example.instagramreelai.ai.SummaryService;
import com.example.instagramreelai.ai.TranscriptionService;
import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.example.instagramreelai.entity.*;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.media.MediaService;
import com.example.instagramreelai.repository.*;
import com.example.instagramreelai.service.EncryptionService;
import com.example.instagramreelai.service.InstagramUserProfileService;
import com.example.instagramreelai.service.KnowledgeService;
import com.example.instagramreelai.service.ReelCacheService;
import com.example.instagramreelai.service.ReelResultService;
import com.example.instagramreelai.service.ResourceDetectionService;
import com.example.instagramreelai.service.WorkerRegistryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final InstagramUserProfileService userProfileService;

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

            Optional<String> mediaUrl = Optional.empty();

            // 1. Direct downloadable media URL from webhook payload
            if (reel.getSourceUrl() != null && !reel.getSourceUrl().isBlank()
                    && (reel.getSourceUrl().startsWith("http://") || reel.getSourceUrl().startsWith("https://"))
                    && !reel.getSourceUrl().contains("instagram.com/reel/")
                    && !reel.getSourceUrl().contains("instagram.com/p/")) {
                mediaUrl = Optional.of(reel.getSourceUrl());
            }

            // 2. Resolve via Instagram API if external media ID exists
            if (mediaUrl.isEmpty() && reel.getExternalMediaId() != null && !reel.getExternalMediaId().isBlank()) {
                mediaUrl = instagramClient.resolveMediaUrl(reel.getExternalMediaId(), accessToken);
            }

            if (mediaUrl.isEmpty()) {
                reel.setProcessingStatus(JobStatus.MEDIA_UNAVAILABLE);
                reelRepository.save(reel);
                jobService.markFailed(job.getId(), workerId, JobStatus.MEDIA_UNAVAILABLE,
                        "MEDIA_NOT_ACCESSIBLE",
                        "Instagram did not provide downloadable media for this content.", true);
                fanOut(reel,
                        "⚠️ I received your Reel, but direct audio/video media was not accessible through the Instagram API for this share.\n\nTip: You can send a direct video or public link!", accountId, accessToken);
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
            knowledgeService.saveExtractedItems(reel, summaryResult);
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
            String senderId = request.getInstagramSenderId();
            String messageToSend = message;

            // Follower-only gating: Check if sender follows @unireelverse.in
            if (senderId != null && !senderId.isBlank()) {
                try {
                    Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
                    if (profileOpt.isPresent()) {
                        InstagramUserProfile profile = profileOpt.get();
                        boolean isFollowing = Boolean.TRUE.equals(profile.getIsUserFollowBusiness());
                        request.setWasFollowingInitially(isFollowing);
                        if (!isFollowing) {
                            messageToSend = formatTeaserReply(reel, profile);
                            log.info("Sender {} is NOT following; sending teaser lead-magnet reply", senderId);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Follower check failed for sender={}: {}", senderId, ex.getMessage());
                }
            }

            boolean delivered = instagramClient.sendReply(
                    accountId, senderId, messageToSend, accessToken);
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

    private String formatTeaserReply(Reel reel, InstagramUserProfile profile) {
        String title = "Reel Highlights";
        String summaryText = "";
        Optional<ReelSummary> summaryOpt = summaryRepository.findByReelId(reel.getId());
        if (summaryOpt.isPresent()) {
            ReelSummary s = summaryOpt.get();
            if (s.getTitle() != null && !s.getTitle().isBlank()) title = s.getTitle();
            if (s.getSummary() != null && !s.getSummary().isBlank()) summaryText = s.getSummary().trim();
        }

        String displayName = profile != null && profile.getName() != null && !profile.getName().isBlank()
                ? profile.getName() : "there";

        StringBuilder sb = new StringBuilder();
        sb.append("🎬 ").append(title).append("\n\n");
        sb.append("👋 Hey ").append(displayName).append("! Here is your quick preview:\n\n");
        if (!summaryText.isBlank()) {
            sb.append("📌 ").append(summaryText).append("\n\n");
        }
        sb.append("🔒 The full breakdown with key data points, exact tool names, links & steps is reserved for our followers!\n\n");
        sb.append("👉 Follow @unireelverse.in and reply \"unlock\" right here to get instant access!");

        return truncate(sb.toString().trim());
    }

    private String formatReply(ReelSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎬 ").append(summary.getTitle() != null && !summary.getTitle().isBlank() ? summary.getTitle() : "Reel Highlights").append("\n\n");

        if (summary.getSummary() != null && !summary.getSummary().isBlank()) {
            sb.append("📌 Summary:\n").append(summary.getSummary().trim()).append("\n\n");
        }

        try {
            if (summary.getKeyPoints() != null && !summary.getKeyPoints().isBlank()) {
                List<String> points = objectMapper.readValue(summary.getKeyPoints(), new TypeReference<List<String>>() {});
                if (points != null && !points.isEmpty()) {
                    sb.append("⚡ Main Points & Key Data:\n");
                    for (String pt : points) {
                        sb.append("• ").append(pt).append("\n");
                    }
                    sb.append("\n");
                }
            }
            if (summary.getToolsOrProducts() != null && !summary.getToolsOrProducts().isBlank()) {
                List<String> tools = objectMapper.readValue(summary.getToolsOrProducts(), new TypeReference<List<String>>() {});
                if (tools != null && !tools.isEmpty()) {
                    sb.append("🛠️ Tools & Mentions: ").append(String.join(", ", tools)).append("\n\n");
                }
            }
            if (summary.getActionItems() != null && !summary.getActionItems().isBlank()) {
                List<String> actions = objectMapper.readValue(summary.getActionItems(), new TypeReference<List<String>>() {});
                if (actions != null && !actions.isEmpty()) {
                    sb.append("💡 Key Takeaway:\n• ").append(actions.get(0)).append("\n");
                }
            }
        } catch (Exception ignored) {}

        sb.append("\n📬 Drop your email below to get the Notion template & resource checklist in your inbox!");

        return truncate(sb.toString().trim());
    }

    private String formatReply(AiSummaryResult summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎬 ").append(summary.getTitle() != null && !summary.getTitle().isBlank() ? summary.getTitle() : "Reel Highlights").append("\n\n");

        if (summary.getSummary() != null && !summary.getSummary().isBlank()) {
            sb.append("📌 Summary:\n").append(summary.getSummary().trim()).append("\n\n");
        }

        if (summary.getKeyPoints() != null && !summary.getKeyPoints().isEmpty()) {
            sb.append("⚡ Main Points & Key Data:\n");
            for (String point : summary.getKeyPoints()) {
                sb.append("• ").append(point).append("\n");
            }
            sb.append("\n");
        }

        if (summary.getToolsOrProducts() != null && !summary.getToolsOrProducts().isEmpty()) {
            sb.append("🛠️ Tools & Mentions: ")
              .append(String.join(", ", summary.getToolsOrProducts()))
              .append("\n\n");
        }

        if (summary.getActionItems() != null && !summary.getActionItems().isEmpty()) {
            sb.append("💡 Key Takeaway:\n• ").append(summary.getActionItems().get(0)).append("\n");
        }

        sb.append("\n📬 Drop your email below to get the Notion template & resource checklist in your inbox!");

        return truncate(sb.toString().trim());
    }

    private String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 997) + "..." : message;
    }
}
