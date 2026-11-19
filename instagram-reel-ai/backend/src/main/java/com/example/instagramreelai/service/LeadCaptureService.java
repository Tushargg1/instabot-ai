package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.CapturedLead;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelRequest;
import com.example.instagramreelai.entity.ReelSummary;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.CapturedLeadRepository;
import com.example.instagramreelai.repository.ReelRequestRepository;
import com.example.instagramreelai.repository.ReelSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCaptureService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private final CapturedLeadRepository leadRepository;
    private final ReelRequestRepository requestRepository;
    private final ReelSummaryRepository summaryRepository;
    private final InstagramUserProfileService userProfileService;

    /**
     * Inspects incoming DM text for an email address.
     * If found, automatically captures the lead and returns an acknowledgment message.
     */
    @Transactional
    public Optional<String> processPotentialEmailLead(String senderId, String messageText) {
        if (messageText == null || messageText.isBlank()) return Optional.empty();

        Matcher matcher = EMAIL_PATTERN.matcher(messageText);
        if (!matcher.find()) return Optional.empty();

        String email = matcher.group(0).toLowerCase();

        // Retrieve requester profile
        String username = null;
        String fullName = null;
        String avatar = null;
        Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
        if (profileOpt.isPresent()) {
            InstagramUserProfile p = profileOpt.get();
            username = p.getUsername();
            fullName = p.getName();
            avatar = p.getProfilePic();
        }

        // Retrieve recent Reel source
        Reel sourceReel = null;
        String sourceReelTitle = null;
        Optional<ReelRequest> reqOpt = requestRepository.findTopByInstagramSenderIdOrderByCreatedAtDesc(senderId);
        if (reqOpt.isPresent()) {
            sourceReel = reqOpt.get().getReel();
            Optional<ReelSummary> sOpt = summaryRepository.findByReelId(sourceReel.getId());
            sourceReelTitle = sOpt.map(ReelSummary::getTitle).orElse("Instagram Reel");
        }

        CapturedLead lead = leadRepository.findByEmailAndInstagramSenderId(email, senderId)
                .orElseGet(() -> CapturedLead.builder()
                        .email(email)
                        .instagramSenderId(senderId)
                        .build());

        lead.setInstagramUsername(username);
        lead.setFullName(fullName);
        lead.setAvatarUrl(avatar);
        lead.setSourceReel(sourceReel);
        lead.setSourceReelTitle(sourceReelTitle);
        lead.setStatus("ACTIVE");
        leadRepository.save(lead);

        log.info("Captured new lead email={} from instagram sender={} (@{})", email, senderId, username);

        String reply = String.format(
                "✅ Awesome! We've saved %s to our VIP list! 📬\n\nYour complete Notion template and tool checklist are on their way to your inbox! 🎉",
                email
        );
        return Optional.of(reply);
    }

    @Transactional(readOnly = true)
    public Page<CapturedLead> getLeads(String query, Pageable pageable) {
        return leadRepository.search(query != null ? query.trim() : null, pageable);
    }
}
