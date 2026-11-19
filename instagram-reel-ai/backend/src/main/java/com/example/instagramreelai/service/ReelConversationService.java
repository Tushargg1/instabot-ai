package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelRequest;
import com.example.instagramreelai.entity.ReelSummary;
import com.example.instagramreelai.entity.ReelTranscript;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.ReelRequestRepository;
import com.example.instagramreelai.repository.ReelSummaryRepository;
import com.example.instagramreelai.repository.ReelTranscriptRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReelConversationService {

    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";

    private final ReelRequestRepository requestRepository;
    private final ReelSummaryRepository summaryRepository;
    private final ReelTranscriptRepository transcriptRepository;
    private final InstagramUserProfileService userProfileService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Attempts to answer a follow-up DM about the last Reel requested by the user.
     * Handles both "unlock" requests (lead-magnet follower verification) and natural Q&A.
     */
    public Optional<String> answerFollowUp(String senderId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return Optional.empty();

        Optional<ReelRequest> lastReqOpt = requestRepository.findTopByInstagramSenderIdOrderByCreatedAtDesc(senderId);
        if (lastReqOpt.isEmpty()) return Optional.empty();

        ReelRequest req = lastReqOpt.get();
        // If request was more than 48 hours ago, treat as normal message
        if (req.getCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(48)))) {
            return Optional.empty();
        }

        Reel reel = req.getReel();
        Optional<ReelSummary> summaryOpt = summaryRepository.findByReelId(reel.getId());
        if (summaryOpt.isEmpty()) return Optional.empty();
        ReelSummary summary = summaryOpt.get();

        String lowerMsg = userMessage.trim().toLowerCase();

        // ── 1. Unlock Command / Lead Magnet Check ──
        if (lowerMsg.contains("unlock") || lowerMsg.contains("link") || lowerMsg.contains("full")) {
            Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
            boolean isFollowing = profileOpt.map(p -> Boolean.TRUE.equals(p.getIsUserFollowBusiness())).orElse(false);

            if (isFollowing) {
                if (Boolean.FALSE.equals(req.getWasFollowingInitially()) && !Boolean.TRUE.equals(req.getConvertedToFollower())) {
                    req.setConvertedToFollower(true);
                    requestRepository.save(req);
                    log.info("Non-follower senderId={} converted to follower via unlock!", senderId);
                }
                return Optional.of("🎉 Thank you for following @unireelverse.in! Here is your full unlocked breakdown:\n\n"
                        + formatFullBreakdown(summary));
            } else {
                return Optional.of("🔒 You are not following @unireelverse.in yet!\n\n"
                        + "👉 Follow us first, then reply \"unlock\" right here to get the complete data points & direct links!");
            }
        }

        // ── 2. Conversational Q&A via Groq ──
        try {
            Optional<ReelTranscript> transcriptOpt = transcriptRepository.findByReelId(reel.getId());
            String transcriptText = transcriptOpt.map(ReelTranscript::getTranscript).orElse("");

            String apiKey = appProperties.getOpenai().getApiKey();
            if (apiKey == null || apiKey.isBlank()) return Optional.empty();

            String systemPrompt = String.format("""
                    You are the friendly AI assistant for Instagram account @unireelverse.in responding in direct messages.
                    The user watched an Instagram Reel and is asking a question in DM.
                    Context:
                    - Reel Title: %s
                    - Reel Summary: %s
                    - Key Points: %s
                    - Tools/Products: %s
                    - Full Spoken Transcript: %s

                    Rules:
                    - Answer concisely, accurately, and naturally under 350 characters.
                    - Use relevant emojis.
                    - Only use facts stated in the Reel context. If not mentioned in the Reel, politely state that the Reel didn't cover that.
                    """,
                    summary.getTitle() != null ? summary.getTitle() : "Reel",
                    summary.getSummary() != null ? summary.getSummary() : "",
                    summary.getKeyPoints() != null ? summary.getKeyPoints() : "",
                    summary.getToolsOrProducts() != null ? summary.getToolsOrProducts() : "",
                    transcriptText.length() > 2500 ? transcriptText.substring(0, 2500) : transcriptText
            );

            Map<String, Object> body = Map.of(
                    "model", DEFAULT_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 250
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_CHAT_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(12))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String answer = json.path("choices").path(0).path("message").path("content").asText(null);
                if (answer != null && !answer.isBlank()) {
                    log.info("Answered Reel Q&A for sender={} msg='{}'", senderId, userMessage);
                    return Optional.of(answer.trim());
                }
            } else {
                log.warn("Groq Q&A call returned status={}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Reel Q&A error for sender={}: {}", senderId, e.getMessage());
        }

        return Optional.empty();
    }

    private String formatFullBreakdown(ReelSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎬 ").append(summary.getTitle() != null ? summary.getTitle() : "Reel Highlights").append("\n\n");
        if (summary.getSummary() != null) {
            sb.append("📌 Summary:\n").append(summary.getSummary().trim()).append("\n\n");
        }
        try {
            if (summary.getKeyPoints() != null && !summary.getKeyPoints().isBlank()) {
                List<String> points = objectMapper.readValue(summary.getKeyPoints(), new TypeReference<List<String>>() {});
                if (points != null && !points.isEmpty()) {
                    sb.append("⚡ Main Points & Key Data:\n");
                    for (String pt : points) sb.append("• ").append(pt).append("\n");
                    sb.append("\n");
                }
            }
            if (summary.getToolsOrProducts() != null && !summary.getToolsOrProducts().isBlank()) {
                List<String> tools = objectMapper.readValue(summary.getToolsOrProducts(), new TypeReference<List<String>>() {});
                if (tools != null && !tools.isEmpty()) {
                    sb.append("🛠️ Tools & Mentions: ").append(String.join(", ", tools)).append("\n\n");
                }
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }
}
