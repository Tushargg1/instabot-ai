package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.MarketingChatMessage;
import com.example.instagramreelai.dto.MarketingLeadRequest;
import com.example.instagramreelai.dto.MarketingStatsDto;
import com.example.instagramreelai.entity.MarketingLead;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.MarketingLeadRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingOutreachService {

    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";

    private final MarketingLeadRepository leadRepository;
    private final InstagramUserProfileService userProfileService;
    private final InstagramClient instagramClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── CRUD & Listing ──

    @Transactional(readOnly = true)
    public Page<MarketingLead> getLeads(String query, String status, String tag, Pageable pageable) {
        return leadRepository.searchLeads(
                query != null ? query.trim() : null,
                status != null && !status.isBlank() ? status.trim() : null,
                tag != null && !tag.isBlank() ? tag.trim() : null,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Optional<MarketingLead> getLead(UUID id) {
        return leadRepository.findById(id);
    }

    @Transactional
    public MarketingLead createLead(MarketingLeadRequest req) {
        String cleanUsername = req.getInstagramUsername().trim().replace("@", "");
        
        MarketingLead lead = MarketingLead.builder()
                .instagramUsername(cleanUsername)
                .instagramSenderId(req.getInstagramSenderId())
                .contactName(req.getContactName())
                .avatarUrl(req.getAvatarUrl())
                .context(req.getContext())
                .tag(req.getTag() != null && !req.getTag().isBlank() ? req.getTag().trim() : "General")
                .productOffer(req.getProductOffer() != null ? req.getProductOffer().trim() : "")
                .salesGoal(req.getSalesGoal() != null && !req.getSalesGoal().isBlank() ? req.getSalesGoal().trim() : "Book call / Buy product")
                .firstMessage(req.getFirstMessage())
                .status(req.getStatus() != null && !req.getStatus().isBlank() ? req.getStatus() : "PENDING")
                .notes(req.getNotes())
                .conversationHistory("[]")
                .build();

        return leadRepository.save(lead);
    }

    @Transactional
    public MarketingLead updateLead(UUID id, MarketingLeadRequest req) {
        MarketingLead lead = leadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Marketing lead not found"));

        if (req.getInstagramUsername() != null) {
            lead.setInstagramUsername(req.getInstagramUsername().trim().replace("@", ""));
        }
        if (req.getInstagramSenderId() != null) lead.setInstagramSenderId(req.getInstagramSenderId());
        if (req.getContactName() != null) lead.setContactName(req.getContactName());
        if (req.getAvatarUrl() != null) lead.setAvatarUrl(req.getAvatarUrl());
        if (req.getContext() != null) lead.setContext(req.getContext());
        if (req.getTag() != null) lead.setTag(req.getTag().trim());
        if (req.getProductOffer() != null) lead.setProductOffer(req.getProductOffer());
        if (req.getSalesGoal() != null) lead.setSalesGoal(req.getSalesGoal());
        if (req.getFirstMessage() != null) lead.setFirstMessage(req.getFirstMessage());
        if (req.getStatus() != null) lead.setStatus(req.getStatus());
        if (req.getNotes() != null) lead.setNotes(req.getNotes());

        return leadRepository.save(lead);
    }

    @Transactional
    public void deleteLead(UUID id) {
        leadRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public MarketingStatsDto getStats() {
        long total = leadRepository.count();
        long pending = leadRepository.countByStatus("PENDING");
        long outreached = leadRepository.countByStatus("OUTREACHED");
        long inConversation = leadRepository.countByStatus("IN_CONVERSATION");
        long converted = leadRepository.countByStatus("CONVERTED");
        long closed = leadRepository.countByStatus("CLOSED");

        double rate = total > 0 ? ((double) converted / total) * 100.0 : 0.0;
        rate = Math.round(rate * 10.0) / 10.0;

        return MarketingStatsDto.builder()
                .totalLeads(total)
                .pending(pending)
                .outreached(outreached)
                .inConversation(inConversation)
                .converted(converted)
                .closed(closed)
                .conversionRate(rate)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getTags() {
        return leadRepository.findDistinctTags();
    }

    // ── AI First Message Generator ──

    public String generateFirstMessage(String context, String tag, String productOffer, String salesGoal, String contactName) {
        String name = contactName != null && !contactName.isBlank() ? contactName : "there";
        String prompt = String.format("""
                You are a world-class outbound copywriter writing the very 1st Instagram direct message (icebreaker) to a prospect.
                
                Prospect Context / Background:
                %s
                
                Niche / Tag: %s
                Prospect Name: %s
                Product / Offer: %s
                Goal: %s
                
                Guidelines:
                1. Sound 100%% authentic, friendly, and human (no corporate jargon or spammy pitches).
                2. Compliment or reference their specific niche/context naturally.
                3. Ask a low-friction, curiosity-inducing question related to their pain point.
                4. DO NOT dump links or sound aggressive in the very 1st text.
                5. Keep it under 250 characters. Include 1-2 relevant emojis.
                Output ONLY the text of the message to send.
                """,
                context != null ? context : "Content creator",
                tag != null ? tag : "General",
                name,
                productOffer != null ? productOffer : "Digital Product / Service",
                salesGoal != null ? salesGoal : "Start conversation"
        );

        return callGroq(prompt, "Draft high-converting Instagram DM icebreaker");
    }

    // ── Live Outbound Send & Status Update ──

    @Transactional
    public MarketingLead markOrSendOutreach(UUID id, String accountId, String accessToken) {
        MarketingLead lead = leadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        String firstMsg = lead.getFirstMessage();
        if (firstMsg == null || firstMsg.isBlank()) {
            firstMsg = generateFirstMessage(lead.getContext(), lead.getTag(), lead.getProductOffer(), lead.getSalesGoal(), lead.getContactName());
            lead.setFirstMessage(firstMsg);
        }

        // If instagramSenderId is present, try sending via Instagram API
        if (lead.getInstagramSenderId() != null && !lead.getInstagramSenderId().isBlank() && accountId != null && accessToken != null) {
            try {
                boolean sent = instagramClient.sendReply(accountId, lead.getInstagramSenderId(), firstMsg, accessToken);
                if (sent) {
                    log.info("Sent initial outreach to senderId={} for lead={}", lead.getInstagramSenderId(), lead.getInstagramUsername());
                }
            } catch (Exception e) {
                log.warn("Could not dispatch via API, marking outreached manually: {}", e.getMessage());
            }
        }

        // Add to conversation history
        List<MarketingChatMessage> history = parseHistory(lead.getConversationHistory());
        history.add(MarketingChatMessage.builder()
                .sender("USER")
                .text(firstMsg)
                .timestamp(Instant.now())
                .build());

        lead.setConversationHistory(serializeHistory(history));
        lead.setStatus("OUTREACHED");
        lead.setOutreachSentAt(Instant.now());
        lead.setLastInteractionAt(Instant.now());

        return leadRepository.save(lead);
    }

    // ── AI Conversational Sales Engine (Triggered by incoming DM) ──

    @Transactional
    public Optional<String> processMarketingLeadReply(String senderId, String messageText, String accountId, String accessToken) {
        if (messageText == null || messageText.isBlank()) return Optional.empty();

        // 1. Check if senderId matches a lead
        Optional<MarketingLead> leadOpt = leadRepository.findByInstagramSenderId(senderId);

        // 2. If not found by senderId, resolve profile to check username
        if (leadOpt.isEmpty()) {
            Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
            if (profileOpt.isPresent() && profileOpt.get().getUsername() != null) {
                String username = profileOpt.get().getUsername();
                leadOpt = leadRepository.findByInstagramUsernameIgnoreCase(username);
                if (leadOpt.isPresent()) {
                    MarketingLead lead = leadOpt.get();
                    lead.setInstagramSenderId(senderId);
                    if (lead.getAvatarUrl() == null) lead.setAvatarUrl(profileOpt.get().getProfilePic());
                    if (lead.getContactName() == null) lead.setContactName(profileOpt.get().getName());
                    leadRepository.save(lead);
                    log.info("Linked senderId={} to marketing lead @{}", senderId, username);
                }
            }
        }

        if (leadOpt.isEmpty()) return Optional.empty();

        MarketingLead lead = leadOpt.get();
        // Skip closed leads
        if ("CLOSED".equalsIgnoreCase(lead.getStatus())) {
            return Optional.empty();
        }

        List<MarketingChatMessage> history = parseHistory(lead.getConversationHistory());
        // Append incoming prospect response
        history.add(MarketingChatMessage.builder()
                .sender("PROSPECT")
                .text(messageText.trim())
                .timestamp(Instant.now())
                .build());

        // Generate high-converting sales reply using Groq
        String replyText = generateSalesAiResponse(lead, history, messageText.trim());

        // Append bot response
        history.add(MarketingChatMessage.builder()
                .sender("BOT")
                .text(replyText)
                .timestamp(Instant.now())
                .build());

        lead.setConversationHistory(serializeHistory(history));
        lead.setStatus("IN_CONVERSATION");
        lead.setLastInteractionAt(Instant.now());
        leadRepository.save(lead);

        // Send via Instagram API
        if (accountId != null && accessToken != null) {
            instagramClient.sendReply(accountId, senderId, replyText, accessToken);
            log.info("Marketing AI Sales Bot replied to lead @{}: '{}'", lead.getInstagramUsername(), replyText);
        }

        return Optional.of(replyText);
    }

    // ── Simulation Engine (Test in Dashboard) ──

    @Transactional
    public String simulateSalesResponse(UUID leadId, String simulatedProspectText) {
        MarketingLead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        List<MarketingChatMessage> history = parseHistory(lead.getConversationHistory());
        history.add(MarketingChatMessage.builder()
                .sender("PROSPECT")
                .text(simulatedProspectText.trim())
                .timestamp(Instant.now())
                .build());

        String replyText = generateSalesAiResponse(lead, history, simulatedProspectText.trim());

        history.add(MarketingChatMessage.builder()
                .sender("BOT")
                .text(replyText)
                .timestamp(Instant.now())
                .build());

        lead.setConversationHistory(serializeHistory(history));
        lead.setStatus("IN_CONVERSATION");
        lead.setLastInteractionAt(Instant.now());
        leadRepository.save(lead);

        return replyText;
    }

    private String generateSalesAiResponse(MarketingLead lead, List<MarketingChatMessage> history, String lastMessage) {
        StringBuilder convoFormatted = new StringBuilder();
        for (MarketingChatMessage msg : history) {
            convoFormatted.append(msg.getSender()).append(": ").append(msg.getText()).append("\n");
        }

        String systemPrompt = String.format("""
                You are a charismatic, consultative AI sales advisor representing @unireelverse.in chatting in Instagram DMs.
                Your mission is to naturally converse with this prospective client, answer their questions, handle any hesitations/objections, and guide them to conversion.
                
                Prospect Information:
                - Username: @%s
                - Name: %s
                - Context / Background: %s
                - Niche / Tag: %s
                
                Product & Offer to Sell:
                %s
                
                Ultimate Goal:
                %s
                
                Conversation Transcript so far:
                %s
                
                Sales Guidelines:
                1. Acknowledge and validate their message with empathy.
                2. Explain directly how our product/solution solves their specific pain point.
                3. If they ask about price, features, or links, give them the exact information transparently with enthusiasm.
                4. Keep the reply punchy, authentic, and under 350 characters. Use friendly emojis.
                5. End with a natural call to action (e.g. asking if they'd like the link or a quick demo).
                6. Output ONLY the DM message to send. No quotes or preambles.
                """,
                lead.getInstagramUsername(),
                lead.getContactName() != null ? lead.getContactName() : "friend",
                lead.getContext() != null ? lead.getContext() : "Creator",
                lead.getTag(),
                lead.getProductOffer(),
                lead.getSalesGoal(),
                convoFormatted.toString()
        );

        return callGroq(systemPrompt, "AI Sales Consultation Response");
    }

    private String callGroq(String prompt, String taskDescription) {
        try {
            String apiKey = appProperties.getOpenai().getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return "Hey! Thanks for reaching out. We'd love to help you with this—check out our link or let me know what questions you have!";
            }

            Map<String, Object> body = Map.of(
                    "model", DEFAULT_MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", prompt),
                            Map.of("role", "user", "content", "Generate the next DM response based on the above.")
                    ),
                    "temperature", 0.4,
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
                String content = json.path("choices").path(0).path("message").path("content").asText(null);
                if (content != null && !content.isBlank()) {
                    return content.trim().replaceAll("^\"|\"$", "");
                }
            } else {
                log.warn("Groq API error ({}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to generate response for {}: {}", taskDescription, e.getMessage());
        }
        return "Hey! Thanks for getting back to me. Would you like me to send over the link and quick breakdown so you can take a look?";
    }

    private List<MarketingChatMessage> parseHistory(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MarketingChatMessage>>() {});
        } catch (Exception e) {
            log.warn("Could not parse conversation history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String serializeHistory(List<MarketingChatMessage> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            log.warn("Could not serialize conversation history: {}", e.getMessage());
            return "[]";
        }
    }
}
