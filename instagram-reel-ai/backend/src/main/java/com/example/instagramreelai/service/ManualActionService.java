package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.ManualActionDto;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.ManualAction;
import com.example.instagramreelai.entity.ManualActionStatus;
import com.example.instagramreelai.entity.ReelResource;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.ManualActionRepository;
import com.example.instagramreelai.repository.ReelResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManualActionService {

    private static final int DEFAULT_LEASE_SECONDS = 300;

    private final ManualActionRepository actionRepository;
    private final AppUserRepository userRepository;
    private final ReelResourceRepository resourceRepository;

    @Transactional(readOnly = true)
    public Page<ManualActionDto> list(Pageable pageable, String status) {
        if (status == null || status.isBlank()) {
            return actionRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
        }
        ManualActionStatus parsed = parseStatus(status);
        return actionRepository.findByStatusOrderByCreatedAtDesc(parsed, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ManualActionDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public ManualActionDto claim(UUID id, String username, Integer requestedLeaseSeconds) {
        AppUser claimant = findUser(username);
        int leaseSeconds = requestedLeaseSeconds == null ? DEFAULT_LEASE_SECONDS : requestedLeaseSeconds;
        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lease must be between 30 and 3600 seconds");
        }

        Instant now = Instant.now();
        Instant leaseExpiresAt = now.plusSeconds(leaseSeconds);
        int updated = actionRepository.claim(id, claimant, now, leaseExpiresAt,
                ManualActionStatus.PENDING, ManualActionStatus.CLAIMED);
        if (updated != 1) {
            if (!actionRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manual action not found");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Manual action is not claimable; it may be completed or have an active lease");
        }

        ManualAction action = find(id);
        ReelResource resource = action.getResource();
        resource.setStatus("ACTION_IN_PROGRESS");
        resourceRepository.save(resource);
        return toDto(action);
    }

    @Transactional
    public ManualActionDto complete(UUID id, String username, String result) {
        AppUser claimant = findUser(username);
        Instant now = Instant.now();
        ManualAction action = findForUpdate(id);
        assertActiveClaim(action, claimant, now);

        action.setStatus(ManualActionStatus.COMPLETED);
        action.setCompletedAt(now);
        action.setLeaseExpiresAt(null);
        action.setResult(result);
        action.setFailureReason(null);
        action.setFailedAt(null);
        action.getResource().setStatus("ACTION_COMPLETED");
        resourceRepository.save(action.getResource());
        return toDto(actionRepository.save(action));
    }

    @Transactional
    public ManualActionDto fail(UUID id, String username, String reason) {
        AppUser claimant = findUser(username);
        Instant now = Instant.now();
        ManualAction action = findForUpdate(id);
        assertActiveClaim(action, claimant, now);

        action.setStatus(ManualActionStatus.FAILED);
        action.setFailedAt(now);
        action.setLeaseExpiresAt(null);
        action.setFailureReason(reason);
        action.getResource().setStatus("ACTION_FAILED");
        resourceRepository.save(action.getResource());
        return toDto(actionRepository.save(action));
    }

    private void assertActiveClaim(ManualAction action, AppUser claimant, Instant now) {
        if (action.getStatus() != ManualActionStatus.CLAIMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual action is not claimed");
        }
        if (action.getClaimedBy() == null || !action.getClaimedBy().getId().equals(claimant.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the current claimant can finish this manual action");
        }
        if (action.getLeaseExpiresAt() == null || !action.getLeaseExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual action lease has expired");
        }
    }

    private ManualAction find(UUID id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Manual action not found"));
    }

    private ManualAction findForUpdate(UUID id) {
        return actionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Manual action not found"));
    }

    private AppUser findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private ManualActionStatus parseStatus(String status) {
        try {
            return ManualActionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown manual action status: " + status);
        }
    }

    private ManualActionDto toDto(ManualAction action) {
        AppUser claimant = action.getClaimedBy();
        return new ManualActionDto(
                action.getId(),
                action.getResource().getId(),
                action.getResource().getReel().getId(),
                action.getActionType(),
                action.getActionPayload(),
                action.getStatus(),
                claimant == null ? null : claimant.getId(),
                claimant == null ? null : claimant.getUsername(),
                action.getClaimedAt(),
                action.getLeaseExpiresAt(),
                action.getCompletedAt(),
                action.getFailedAt(),
                action.getResult(),
                action.getFailureReason(),
                action.getCreatedAt(),
                action.getUpdatedAt());
    }
}
