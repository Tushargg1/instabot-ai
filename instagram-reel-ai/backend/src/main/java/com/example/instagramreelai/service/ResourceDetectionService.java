package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.ReelResourceDto;
import com.example.instagramreelai.dto.ResourceDetectionResult;
import com.example.instagramreelai.entity.ManualAction;
import com.example.instagramreelai.entity.ManualActionStatus;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelResource;
import com.example.instagramreelai.repository.ManualActionRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.ReelResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ResourceDetectionService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String ACTION_REQUIRED = "ACTION_REQUIRED";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final List<ManualActionStatus> OPEN_ACTIONS =
            List.of(ManualActionStatus.PENDING, ManualActionStatus.CLAIMED);

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\\[\\]{}\\\"']+");
    private static final Pattern COMMENT = Pattern.compile(
            "(?iu)\\b(?:comment|reply)\\s+(?:with\\s+)?(?:the\\s+)?(?:(?:word|keyword)\\s+)?[\\\"'`]?([\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,49})");
    private static final Pattern DM = Pattern.compile(
            "(?iu)\\b(?:(?:dm|message)\\s+(?:me\\s+)?|send\\s+me\\s+(?:a\\s+)?(?:dm|message)\\s+)(?:with\\s+)?(?:the\\s+)?(?:(?:word|keyword)\\s+)?[\\\"'`]?([\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,49})");
    private static final Pattern LINK_IN_BIO = Pattern.compile(
            "(?iu)\\b(?:link(?:'s|\\s+is)?\\s+(?:in|on)\\s+(?:my|our|the)\\s+bio|check\\s+(?:my|our|the)\\s+bio|bio\\s+link)\\b");
    private static final Pattern FOLLOW = Pattern.compile(
            "(?iu)\\b(?:follow(?:ing)?\\s+(?:me|us|@[\\p{L}\\p{N}._]+|my\\s+account|our\\s+account)|must\\s+be\\s+following|follow\\s+for)\\b");
    private static final Set<String> NON_KEYWORDS = Set.of(
            "a", "an", "and", "below", "down", "for", "here", "if", "in", "me",
            "on", "the", "this", "to", "what", "with", "your");

    private final ReelRepository reelRepository;
    private final ReelResourceRepository resourceRepository;
    private final ManualActionRepository actionRepository;

    @Value("${app.resources.ttl-seconds:86400}")
    private long resourceTtlSeconds;

    @Transactional
    public ReelResource detectAndPersist(UUID reelId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new IllegalArgumentException("Reel not found: " + reelId));
        String transcript = reel.getTranscript() == null ? null : reel.getTranscript().getTranscript();
        ResourceDetectionResult detection = detect(transcript, reel.getCaption());

        ReelResource resource = resourceRepository.findByReelId(reelId)
                .orElseGet(() -> ReelResource.builder().reel(reel).requestCount(0L).build());
        Instant now = Instant.now();
        resource.setStatus(detection.status());
        resource.setResourceType(detection.resourceType());
        resource.setResourceUrl(detection.resourceUrl());
        resource.setRequiredAction(detection.requiredAction());
        resource.setActionPayload(resolvePayload(detection, reel));
        resource.setLastVerifiedAt(now);
        resource.setLastRefreshAttemptAt(now);
        resource.setLastRefreshError(null);
        resource.setRefreshFailureCount(0);
        resource.setRefreshMetadata("{\"outcome\":\"SUCCESS\"}");
        resource.setExpiresAt(now.plusSeconds(Math.max(60, resourceTtlSeconds)));
        resource.setExtractedData(evidenceJson(detection));
        if (!NOT_FOUND.equals(detection.status()) && resource.getFirstFoundAt() == null) {
            resource.setFirstFoundAt(now);
        }
        resource = resourceRepository.saveAndFlush(resource);
        reconcileManualAction(resource);
        return resource;
    }

    @Transactional
    public ReelResource refreshPreservingLastUseful(UUID reelId) {
        ReelResource existing = resourceRepository.findByReelId(reelId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found for Reel: " + reelId));
        String oldUrl = existing.getResourceUrl();
        String oldType = existing.getResourceType();
        String oldData = existing.getExtractedData();
        String oldStatus = existing.getStatus();
        String oldAction = existing.getRequiredAction();
        String oldPayload = existing.getActionPayload();
        Instant now = Instant.now();
        try {
            ReelResource refreshed = detectAndPersist(reelId);
            if (NOT_FOUND.equals(refreshed.getStatus()) && (oldUrl != null || oldAction != null)) {
                refreshed.setResourceUrl(oldUrl); refreshed.setResourceType(oldType);
                refreshed.setExtractedData(oldData); refreshed.setStatus(oldStatus);
                refreshed.setRequiredAction(oldAction); refreshed.setActionPayload(oldPayload);
                refreshed.setLastRefreshError("Refresh found no replacement; preserved last useful resource");
                refreshed.setRefreshFailureCount(existing.getRefreshFailureCount() + 1);
                refreshed.setRefreshMetadata("{\"outcome\":\"PRESERVED\"}");
                return resourceRepository.save(refreshed);
            }
            return refreshed;
        } catch (RuntimeException exception) {
            existing.setLastRefreshAttemptAt(now);
            existing.setLastRefreshError(exception.getMessage());
            existing.setRefreshFailureCount((existing.getRefreshFailureCount() == null ? 0
                    : existing.getRefreshFailureCount()) + 1);
            existing.setRefreshMetadata("{\"outcome\":\"FAILED\"}");
            return resourceRepository.save(existing);
        }
    }

    @Transactional(readOnly = true)
    public Page<ReelResourceDto> list(UUID ownerId, Pageable pageable) {
        return resourceRepository.findByReelUserIdOrderByCreatedAtDesc(ownerId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ReelResourceDto detail(UUID id, UUID ownerId) {
        return toDto(resourceRepository.findByIdAndReelUserId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")));
    }

    @Transactional
    public ReelResourceDto requestRefresh(UUID id, UUID ownerId) {
        ReelResource resource = resourceRepository.findByIdAndReelUserId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
        return toDto(refreshPreservingLastUseful(resource.getReel().getId()));
    }

    private ReelResourceDto toDto(ReelResource value) {
        return new ReelResourceDto(value.getId(), value.getReel().getId(), value.getResourceUrl(),
                value.getResourceType(), value.getExtractedData(), value.getStatus(),
                value.getRequiredAction(), value.getActionPayload(), value.getRequestCount(),
                value.getFirstFoundAt(), value.getLastRequestedAt(), value.getLastVerifiedAt(),
                value.getExpiresAt(), value.getLastRefreshAttemptAt(), value.getLastRefreshError(),
                value.getRefreshFailureCount(), value.getRefreshMetadata(), value.getCreatedAt(),
                value.getUpdatedAt());
    }

    public ResourceDetectionResult detect(String transcript, String caption) {
        Candidate url = firstMatch(URL, transcript, caption, 0);
        if (url != null) {
            return new ResourceDetectionResult(AVAILABLE, "URL", normalizeUrl(url.value()),
                    null, null, url.source());
        }

        Candidate comment = keywordMatch(COMMENT, transcript, caption);
        if (comment != null) {
            return action("COMMENT_KEYWORD", "COMMENT", comment);
        }

        Candidate dm = keywordMatch(DM, transcript, caption);
        if (dm != null) {
            return action("DM_KEYWORD", "DM", dm);
        }

        Candidate bio = firstMatch(LINK_IN_BIO, transcript, caption, 0);
        if (bio != null) {
            return action("LINK_IN_BIO", "LINK_IN_BIO", new Candidate(null, bio.source()));
        }

        Candidate follow = firstMatch(FOLLOW, transcript, caption, 0);
        if (follow != null) {
            return action("FOLLOW_REQUIRED", "FOLLOW", new Candidate(null, follow.source()));
        }

        return new ResourceDetectionResult(NOT_FOUND, null, null, null, null, null);
    }

    private ResourceDetectionResult action(String type, String requiredAction, Candidate candidate) {
        return new ResourceDetectionResult(ACTION_REQUIRED, type, null, requiredAction,
                candidate.value(), candidate.source());
    }

    private Candidate keywordMatch(Pattern pattern, String transcript, String caption) {
        Candidate transcriptMatch = keywordMatchInText(pattern, transcript, "transcript");
        return transcriptMatch != null
                ? transcriptMatch
                : keywordMatchInText(pattern, caption, "caption");
    }

    private Candidate keywordMatchInText(Pattern pattern, String text, String source) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = trimTrailingPunctuation(matcher.group(1));
            if (!NON_KEYWORDS.contains(value.toLowerCase(Locale.ROOT))) {
                return new Candidate(value.toUpperCase(Locale.ROOT), source);
            }
        }
        return null;
    }

    private Candidate firstMatch(Pattern pattern, String transcript, String caption, int group) {
        Candidate transcriptMatch = match(pattern, transcript, "transcript", group);
        return transcriptMatch != null ? transcriptMatch : match(pattern, caption, "caption", group);
    }

    private Candidate match(Pattern pattern, String text, String source, int group) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? new Candidate(trimTrailingPunctuation(matcher.group(group)), source) : null;
    }

    private String normalizeUrl(String value) {
        String normalized = trimTrailingPunctuation(value);
        return normalized.regionMatches(true, 0, "www.", 0, 4)
                ? "https://" + normalized
                : normalized;
    }

    private String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private String resolvePayload(ResourceDetectionResult detection, Reel reel) {
        if (detection.actionPayload() != null) {
            return detection.actionPayload();
        }
        if (detection.requiresManualAction()) {
            return reel.getCreatorUsername();
        }
        return null;
    }

    private String evidenceJson(ResourceDetectionResult detection) {
        return "{\"rule\":\"" + jsonEscape(
                detection.resourceType() == null ? "NONE" : detection.resourceType())
                + "\",\"source\":" + (detection.evidenceSource() == null
                ? "null" : "\"" + jsonEscape(detection.evidenceSource()) + "\"") + "}";
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void reconcileManualAction(ReelResource resource) {
        List<ManualAction> open = actionRepository
                .findByResourceIdAndStatusInOrderByCreatedAtAsc(resource.getId(), OPEN_ACTIONS);
        if (resource.getRequiredAction() == null) {
            open.forEach(this::cancel);
            return;
        }

        ManualAction retained = null;
        for (ManualAction action : open) {
            boolean same = action.getActionType().equals(resource.getRequiredAction())
                    && Objects.equals(action.getActionPayload(), resource.getActionPayload());
            if (same && retained == null) {
                retained = action;
            } else {
                cancel(action);
            }
        }
        if (retained == null) {
            actionRepository.save(ManualAction.builder()
                    .resource(resource)
                    .actionType(resource.getRequiredAction())
                    .actionPayload(resource.getActionPayload())
                    .status(ManualActionStatus.PENDING)
                    .build());
        }
    }

    private void cancel(ManualAction action) {
        action.setStatus(ManualActionStatus.CANCELLED);
        action.setLeaseExpiresAt(null);
        actionRepository.save(action);
    }

    private record Candidate(String value, String source) {
    }
}
