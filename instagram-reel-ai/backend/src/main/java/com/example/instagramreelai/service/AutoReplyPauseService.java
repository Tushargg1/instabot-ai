package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.MarketingLead;
import com.example.instagramreelai.entity.PausedAutoReplyUser;
import com.example.instagramreelai.repository.MarketingLeadRepository;
import com.example.instagramreelai.repository.PausedAutoReplyUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoReplyPauseService {

    private final PausedAutoReplyUserRepository pausedUserRepository;
    private final MarketingLeadRepository marketingLeadRepository;

    @Transactional(readOnly = true)
    public boolean isAutoReplyPaused(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String clean = identifier.trim();

        // 1. Check paused_auto_reply_users table
        List<PausedAutoReplyUser> pausedMatches = pausedUserRepository.findAnyMatching(clean);
        if (!pausedMatches.isEmpty()) {
            return true;
        }

        // 2. Check marketing_leads table by sender ID or username
        Optional<MarketingLead> bySender = marketingLeadRepository.findByInstagramSenderId(clean);
        if (bySender.isPresent() && Boolean.TRUE.equals(bySender.get().getAutoReplyPaused())) {
            return true;
        }

        Optional<MarketingLead> byUsername = marketingLeadRepository.findByInstagramUsernameIgnoreCase(clean.replace("@", ""));
        if (byUsername.isPresent() && Boolean.TRUE.equals(byUsername.get().getAutoReplyPaused())) {
            return true;
        }

        return false;
    }

    @Transactional
    public void pauseUser(String userIdentifier, String username, String senderId, String reason) {
        if (userIdentifier == null || userIdentifier.isBlank()) return;
        String idKey = userIdentifier.trim().toLowerCase();

        Optional<PausedAutoReplyUser> existing = pausedUserRepository.findByUserIdentifierIgnoreCase(idKey);
        if (existing.isEmpty()) {
            PausedAutoReplyUser paused = PausedAutoReplyUser.builder()
                    .userIdentifier(idKey)
                    .username(username != null ? username.trim().replace("@", "") : null)
                    .senderId(senderId != null ? senderId.trim() : null)
                    .reason(reason)
                    .pausedBy("admin")
                    .build();
            pausedUserRepository.save(paused);
            log.info("Auto-reply PAUSED for user identifier: {}", idKey);
        }

        // Sync with MarketingLead if present
        syncMarketingLeadState(username != null ? username : userIdentifier, senderId, true);
    }

    @Transactional
    public void resumeUser(String userIdentifier) {
        if (userIdentifier == null || userIdentifier.isBlank()) return;
        String idKey = userIdentifier.trim();

        List<PausedAutoReplyUser> matches = pausedUserRepository.findAnyMatching(idKey);
        if (!matches.isEmpty()) {
            pausedUserRepository.deleteAll(matches);
            log.info("Auto-reply RESUMED for user identifier: {}", idKey);
        }

        // Sync with MarketingLead if present
        syncMarketingLeadState(userIdentifier, idKey, false);
    }

    @Transactional
    public boolean toggleLeadPause(UUID leadId) {
        MarketingLead lead = marketingLeadRepository.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

        boolean newPausedState = !Boolean.TRUE.equals(lead.getAutoReplyPaused());
        lead.setAutoReplyPaused(newPausedState);
        marketingLeadRepository.save(lead);

        String username = lead.getInstagramUsername();
        String senderId = lead.getInstagramSenderId();

        if (newPausedState) {
            pauseUser(username, username, senderId, "Paused via Marketing Lead CRM");
        } else {
            resumeUser(username);
            if (senderId != null) {
                resumeUser(senderId);
            }
        }

        log.info("Toggled auto-reply pause for lead @{} -> {}", username, newPausedState ? "PAUSED" : "ACTIVE");
        return newPausedState;
    }

    @Transactional(readOnly = true)
    public Set<String> getPausedUserIdentifiers() {
        Set<String> result = new HashSet<>();
        List<PausedAutoReplyUser> all = pausedUserRepository.findAll();
        for (PausedAutoReplyUser u : all) {
            if (u.getUserIdentifier() != null) result.add(u.getUserIdentifier().toLowerCase());
            if (u.getUsername() != null) result.add(u.getUsername().toLowerCase());
            if (u.getSenderId() != null) result.add(u.getSenderId());
        }

        List<MarketingLead> leads = marketingLeadRepository.findAll();
        for (MarketingLead l : leads) {
            if (Boolean.TRUE.equals(l.getAutoReplyPaused())) {
                if (l.getInstagramUsername() != null) result.add(l.getInstagramUsername().toLowerCase());
                if (l.getInstagramSenderId() != null) result.add(l.getInstagramSenderId());
            }
        }

        return result;
    }

    private void syncMarketingLeadState(String username, String senderId, boolean paused) {
        if (username != null) {
            String u = username.trim().replace("@", "");
            marketingLeadRepository.findByInstagramUsernameIgnoreCase(u).ifPresent(lead -> {
                lead.setAutoReplyPaused(paused);
                marketingLeadRepository.save(lead);
            });
        }
        if (senderId != null) {
            marketingLeadRepository.findByInstagramSenderId(senderId.trim()).ifPresent(lead -> {
                lead.setAutoReplyPaused(paused);
                marketingLeadRepository.save(lead);
            });
        }
    }
}
