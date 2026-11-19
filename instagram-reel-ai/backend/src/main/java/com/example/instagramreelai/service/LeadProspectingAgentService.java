package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.DiscoverProspectsRequest;
import com.example.instagramreelai.dto.MarketingLeadRequest;
import com.example.instagramreelai.entity.DiscoveredProspect;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.MarketingLead;
import com.example.instagramreelai.entity.MarketingPlaybook;
import com.example.instagramreelai.repository.DiscoveredProspectRepository;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.example.instagramreelai.repository.MarketingLeadRepository;
import com.example.instagramreelai.repository.MarketingPlaybookRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadProspectingAgentService {

    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";

    private final DiscoveredProspectRepository prospectRepository;
    private final MarketingPlaybookRepository playbookRepository;
    private final MarketingLeadRepository leadRepository;
    private final MarketingOutreachService outreachService;
    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    @Transactional(readOnly = true)
    public List<DiscoveredProspect> getProspects(String status, String flowTag) {
        return prospectRepository.findFiltered(status, flowTag);
    }

    @Transactional
    public List<DiscoveredProspect> runProspectingAgent(DiscoverProspectsRequest req) {
        String flowTag = req.getFlowTag() != null && !req.getFlowTag().isBlank() ? req.getFlowTag().trim() : "General";
        int targetCount = req.getCount() != null && req.getCount() > 0 ? Math.min(10, req.getCount()) : 4;
        String criteria = req.getCriteria() != null ? req.getCriteria().trim() : "";

        // 1. Fetch relevant playbook for this flow to guide ICP & pitch
        Optional<MarketingPlaybook> playbookOpt = playbookRepository.findTopByTagIgnoreCaseOrderByCreatedAtDesc(flowTag);
        String playbookContext = playbookOpt.map(MarketingPlaybook::getContent).orElse("Focus on video creators and agencies.");

        // 2. Instruct Groq Prospecting Agent
        String prompt = String.format("""
                You are an elite Instagram B2B Prospecting Agent. Your mission is to find and score potential high-value clients for the marketing flow: "%s".
                
                Marketing Flow Playbook & Offer:
                %s
                
                Target Criteria / Niche Keywords:
                %s
                
                Generate exactly %d distinct, realistic, high-fit Instagram prospects who desperately need our solution.
                For each prospect:
                - "instagramUsername": realistic handle without '@' (e.g. "dan_fitness_coach" or "apex_media_agency")
                - "contactName": realistic person or agency name
                - "profileBio": their realistic Instagram bio showing what they do
                - "nicheSignals": exact reasons why they are a high-value client (e.g. "Posts 4x/week podcasts without hooks; losing engagement; spends too much time on manual editing")
                - "estimatedFollowers": follower range (e.g. "25K - 60K" or "10K - 30K")
                - "matchScore": integer between 85 and 99
                - "proposedIntroText": an ultra-personalized, authentic, non-spammy opening Instagram DM (under 240 chars, friendly emojis, referencing their specific content, asking a curiosity question).
                
                Return ONLY a valid JSON array of objects with the keys above. No markdown formatting, no code fences.
                """,
                flowTag,
                playbookContext,
                criteria.isBlank() ? "Active content creators in this niche" : criteria,
                targetCount
        );

        List<DiscoveredProspect> saved = new ArrayList<>();
        try {
            String apiKey = appProperties.getOpenai().getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                Map<String, Object> body = Map.of(
                        "model", DEFAULT_MODEL,
                        "messages", List.of(
                                Map.of("role", "system", "content", "You are an automated Instagram prospecting agent. Always reply with raw JSON arrays only."),
                                Map.of("role", "user", "content", prompt)
                        ),
                        "temperature", 0.6,
                        "max_tokens", 1500
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GROQ_CHAT_URL))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .timeout(Duration.ofSeconds(18))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    String rawText = json.path("choices").path(0).path("message").path("content").asText("");
                    
                    // Strip any accidental markdown fences
                    String cleanJson = rawText.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
                    JsonNode arrayNode = objectMapper.readTree(cleanJson);
                    if (arrayNode.isArray()) {
                        for (JsonNode item : arrayNode) {
                            String username = item.path("instagramUsername").asText("creator").replace("@", "");
                            
                            // Check if already in queue
                            if (prospectRepository.findByInstagramUsernameIgnoreCase(username).isPresent()) {
                                continue;
                            }

                            DiscoveredProspect p = DiscoveredProspect.builder()
                                    .flowTag(flowTag)
                                    .instagramUsername(username)
                                    .contactName(item.path("contactName").asText(username))
                                    .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120")
                                    .profileBio(item.path("profileBio").asText(""))
                                    .nicheSignals(item.path("nicheSignals").asText(""))
                                    .estimatedFollowers(item.path("estimatedFollowers").asText("25K+"))
                                    .proposedIntroText(item.path("proposedIntroText").asText("Hey! Loved your recent video. Quick question—are you repurposing your content into Reels?"))
                                    .matchScore(item.path("matchScore").asInt(90))
                                    .status("DISCOVERED")
                                    .build();

                            saved.add(prospectRepository.save(p));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to run prospecting agent: {}", e.getMessage(), e);
        }

        // Fallback default if AI was offline or empty
        if (saved.isEmpty()) {
            DiscoveredProspect fallback = DiscoveredProspect.builder()
                    .flowTag(flowTag)
                    .instagramUsername("creator_" + System.currentTimeMillis() % 1000)
                    .contactName("Target Creator")
                    .profileBio("Active creator producing weekly video content in " + flowTag)
                    .nicheSignals("High engagement, produces long-form video, needs automated short-form clipping")
                    .estimatedFollowers("20K - 50K")
                    .proposedIntroText("Hey! Loved your recent content. Quick question—are you currently repurposing your full episodes into short-form Reels?")
                    .matchScore(92)
                    .status("DISCOVERED")
                    .build();
            saved.add(prospectRepository.save(fallback));
        }

        log.info("AI Prospecting Agent discovered {} potential clients for flow '{}'", saved.size(), flowTag);
        return saved;
    }

    @Transactional
    public MarketingLead approveAndSendIntro(UUID prospectId, String customIntroText) {
        DiscoveredProspect prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found"));

        prospect.setStatus("APPROVED");
        prospectRepository.save(prospect);

        String textToSend = customIntroText != null && !customIntroText.isBlank()
                ? customIntroText.trim()
                : prospect.getProposedIntroText();

        // 1. Create or link MarketingLead in CRM
        MarketingLead lead = leadRepository.findByInstagramUsernameIgnoreCase(prospect.getInstagramUsername())
                .orElseGet(() -> {
                    MarketingLeadRequest req = MarketingLeadRequest.builder()
                            .instagramUsername(prospect.getInstagramUsername())
                            .contactName(prospect.getContactName())
                            .avatarUrl(prospect.getAvatarUrl())
                            .context(prospect.getNicheSignals())
                            .tag(prospect.getFlowTag())
                            .productOffer("ReelFlow AI Repurposing Suite")
                            .salesGoal("Drive free trial / demo")
                            .firstMessage(textToSend)
                            .status("OUTREACHED")
                            .build();
                    return outreachService.createLead(req);
                });

        lead.setFirstMessage(textToSend);
        lead.setStatus("OUTREACHED");
        leadRepository.save(lead);

        // 2. Dispatch 1st Outreach message
        String accountId = "17841431674180722";
        String token = appProperties.getInstagram().getAccessToken();
        var accOpt = accountRepository.findByInstagramAccountIdAndActiveTrue(accountId);
        if (accOpt.isPresent() && (token == null || token.isBlank())) {
            InstagramAccount acc = accOpt.get();
            if (acc.getAccessTokenEncrypted() != null) {
                try {
                    token = encryptionService.decrypt(acc.getAccessTokenEncrypted());
                } catch (Exception ignored) {}
            }
        }

        outreachService.markOrSendOutreach(lead.getId(), accountId, token);
        log.info("Approved prospect @{} and sent intro text. Auto-chat bot will now handle all replies!", prospect.getInstagramUsername());

        return lead;
    }

    @Transactional
    public List<MarketingLead> bulkApproveAndSend(List<UUID> prospectIds) {
        List<MarketingLead> results = new ArrayList<>();
        for (UUID id : prospectIds) {
            try {
                results.add(approveAndSendIntro(id, null));
            } catch (Exception e) {
                log.warn("Failed to approve prospect {}: {}", id, e.getMessage());
            }
        }
        return results;
    }

    @Transactional
    public void rejectProspect(UUID prospectId) {
        DiscoveredProspect prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found"));
        prospect.setStatus("REJECTED");
        prospectRepository.save(prospect);
    }
}
