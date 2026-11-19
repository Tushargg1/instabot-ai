package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.DmReplyRuleDto;
import com.example.instagramreelai.dto.DmReplyRuleRequest;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.DmReplyRule;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.DmReplyRuleRepository;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Manages DM auto-reply rules and executes them against incoming messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DmAutoReplyService {

    private final DmReplyRuleRepository ruleRepository;
    private final AppUserRepository userRepository;
    private final InstagramAccountRepository accountRepository;
    private final InstagramClient instagramClient;
    private final EncryptionService encryptionService;
    private final com.example.instagramreelai.config.AppProperties appProperties;
    private final InstagramUserProfileService userProfileService;

    // ------------------------------------------------------------------ CRUD

    public List<DmReplyRuleDto> getRules(UUID userId) {
        return ruleRepository.findByUserIdOrdered(userId)
                .stream().map(DmReplyRuleDto::from).toList();
    }

    @Transactional
    public DmReplyRuleDto createRule(UUID userId, DmReplyRuleRequest req) {
        validate(req);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DmReplyRule rule = DmReplyRule.builder()
                .user(user)
                .name(req.getName())
                .matchType(req.getMatchType())
                .matchValue(req.getMatchValue())
                .replyText(req.getReplyText())
                .priority(req.getPriority() != null ? req.getPriority() : 100)
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .build();

        return DmReplyRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public DmReplyRuleDto updateRule(UUID userId, UUID ruleId, DmReplyRuleRequest req) {
        validate(req);
        DmReplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        rule.setName(req.getName());
        rule.setMatchType(req.getMatchType());
        rule.setMatchValue(req.getMatchValue());
        rule.setReplyText(req.getReplyText());
        if (req.getPriority() != null) rule.setPriority(req.getPriority());
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());

        return DmReplyRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        DmReplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        ruleRepository.delete(rule);
    }

    @Transactional
    public DmReplyRuleDto toggleRule(UUID userId, UUID ruleId, boolean enabled) {
        DmReplyRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        rule.setEnabled(enabled);
        return DmReplyRuleDto.from(ruleRepository.save(rule));
    }

    // ------------------------------------------------- AUTO-REPLY EXECUTION

    /**
     * Called by {@link WebhookService} for every incoming DM.
     *
     * @param senderId    Instagram user ID of the person who sent the DM
     * @param messageText The plain text of the message (may be blank for media DMs)
     * @param accountId   The Instagram account ID that received the DM
     */
    public void handleIncomingDm(String senderId, String messageText, String accountId) {
        // Find the account to get its access token + owning user
        Optional<InstagramAccount> accountOpt =
                accountRepository.findByInstagramAccountIdAndActiveTrue(accountId);
        if (accountOpt.isEmpty()) {
            // Fall back to any active account (matches WebhookService dev-mode behaviour)
            accountOpt = accountRepository.findAll().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getActive()))
                    .findFirst();
        }
        if (accountOpt.isEmpty()) {
            log.warn("DM auto-reply: no active Instagram account found for accountId={}", accountId);
            return;
        }
        InstagramAccount account = accountOpt.get();

        // Don't reply to ourselves
        if (accountId.equals(senderId)) {
            log.debug("DM auto-reply: skipping echo (sender == account)");
            return;
        }

        // Look up rules belonging to the account owner
        AppUser owner = account.getUser();
        if (owner == null) {
            log.warn("DM auto-reply: account {} has no linked user", accountId);
            return;
        }

        List<DmReplyRule> rules = ruleRepository.findEnabledByUserIdOrdered(owner.getId());
        if (rules.isEmpty()) return;

        String text = messageText != null ? messageText : "";
        DmReplyRule matched = firstMatch(rules, text);
        if (matched == null) {
            log.debug("DM auto-reply: no rule matched for sender={}", senderId);
            return;
        }

        String rawReply = matched.getReplyText();
        String reply = rawReply.replace("{sender_id}", senderId);

        // Fast path: Only query Meta profile API if template actually uses {name} or {username}
        if (rawReply.contains("{name}") || rawReply.contains("{username}")) {
            var profileOpt = userProfileService.getProfile(senderId);
            if (profileOpt.isPresent()) {
                var profile = profileOpt.get();
                String displayName = profile.getName() != null && !profile.getName().isBlank()
                        ? profile.getName()
                        : (profile.getUsername() != null ? profile.getUsername() : "there");
                reply = reply.replace("{name}", displayName)
                             .replace("{username}", profile.getUsername() != null ? "@" + profile.getUsername() : "");
            } else {
                reply = reply.replace("{name}", "there").replace("{username}", "");
            }
        }

        // Fast path: check in-memory configured token first (0ms)
        String accessToken = appProperties.getInstagram().getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            if (account.getAccessTokenEncrypted() != null && !account.getAccessTokenEncrypted().isBlank()) {
                try {
                    accessToken = encryptionService.decrypt(account.getAccessTokenEncrypted());
                } catch (Exception e) {
                    log.warn("Could not decrypt account token: {}", e.getMessage());
                }
            }
        }
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("DM auto-reply: no valid access token for account {}", accountId);
            return;
        }

        boolean sent = instagramClient.sendReply(accountId, senderId, reply, accessToken);
        if (sent) {
            log.info("DM auto-reply sent: rule='{}' sender={}", matched.getName(), senderId);
        } else {
            log.warn("DM auto-reply FAILED: rule='{}' sender={}", matched.getName(), senderId);
        }
    }

    // ---------------------------------------------------------------- HELPERS

    private DmReplyRule firstMatch(List<DmReplyRule> rules, String text) {
        for (DmReplyRule rule : rules) {
            if (matches(rule, text)) return rule;
        }
        return null;
    }

    private boolean matches(DmReplyRule rule, String text) {
        return switch (rule.getMatchType()) {
            case ALL -> true;
            case KEYWORD -> {
                String kw = rule.getMatchValue();
                yield kw != null && text.toLowerCase().contains(kw.toLowerCase());
            }
            case REGEX -> {
                String regex = rule.getMatchValue();
                if (regex == null || regex.isBlank()) yield false;
                try {
                    yield Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text).find();
                } catch (Exception e) {
                    log.warn("DM auto-reply: invalid regex '{}': {}", regex, e.getMessage());
                    yield false;
                }
            }
        };
    }

    private void validate(DmReplyRuleRequest req) {
        if (req.getName() == null || req.getName().isBlank())
            throw new IllegalArgumentException("Rule name is required");
        if (req.getMatchType() == null)
            throw new IllegalArgumentException("matchType is required (KEYWORD, REGEX, ALL)");
        if (req.getMatchType() != DmReplyRule.MatchType.ALL
                && (req.getMatchValue() == null || req.getMatchValue().isBlank()))
            throw new IllegalArgumentException("matchValue is required for KEYWORD and REGEX rules");
        if (req.getReplyText() == null || req.getReplyText().isBlank())
            throw new IllegalArgumentException("replyText is required");
    }
}
