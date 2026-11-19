package com.example.instagramreelai.ai;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.example.instagramreelai.dto.ExtractedKnowledgeItemDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groq API summarization using Llama 3.3 70B (free tier).
 * Free tier: ~30 req/min, ~6000 tokens/min on 70B models.
 *
 * Groq uses the OpenAI-compatible chat completions API.
 * Get your free key at: https://console.groq.com
 */
@Slf4j
@Service
@Primary
@Profile("!dev")
public class GroqSummaryService implements SummaryService {

    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    // Groq retired llama-3.3-70b-versatile; gpt-oss-120b is the current general-purpose
    // model on the free tier that honours response_format=json_object.
    private static final String DEFAULT_MODEL = "openai/gpt-oss-120b";

    private static final String SYSTEM_PROMPT = """
            You are an expert AI intelligence and knowledge extraction engine for Instagram Reels.
            The user wants:
            1. A SHORT and PRECISE breakdown: just main points, key data, and actionable takeaways.
            2. Structured categorization of all distinct entities, tools, jobs, repositories, skills, projects, or resources discussed in the reel, grouped by category and granular sub-category.

            Rules:
            - Be concise, direct, and factual. Avoid filler like "In this reel the speaker discusses...".
            - Extract exact numbers, metrics, statistics, tools, websites, salaries, requirements, steps, and key data.
            - Keep the summary punchy (2 sentences max).
            - List 3 to 5 clear, bulleted key points highlighting core value and specific facts.
            - In "extractedItems", extract 1 or more distinct entries for each tool, job role, github repo, skill, technique, website, or project mentioned in the reel.
              * "category": Choose an intuitive high-level group (e.g., "Tools", "Jobs", "GitHub Repositories", "Skills & Guides", "Projects & Ideas", "AI Models", "Automation", "Finance & Business").
              * "subCategory": Differentiate into specific sub-parts (e.g. for Tools: "Video Clipping", "Voice AI", "Developer Tools", "SEO"; for Jobs: "Remote Engineering", "Prompt Engineering"; for Skills: "Web Development", "Content Creation").
              * "title": Specific name of the item (e.g., "OpusClip", "ElevenLabs", "Staff Prompt Engineer", "Significant-Gravitas/AutoGPT").
              * "summary": 1-2 sentence description of what it does, what is offered, or what is taught.
              * "keyData": Key facts, pricing, free quota, salary, requirements, github stars, or links.
              * "url": Web link if mentioned or known (e.g., "https://opus.pro", "https://github.com/..."), or null.
              * "tags": List of 2-4 keywords (e.g. ["video", "clipping", "ai"]).

            Return ONLY valid JSON matching this exact schema:
            {
              "title": "string",
              "summary": "string",
              "keyPoints": ["string"],
              "actionItems": ["string"],
              "topics": ["string"],
              "toolsOrProducts": ["string"],
              "peopleOrCompanies": ["string"],
              "claimsToVerify": ["string"],
              "language": "string",
              "confidence": 0.0,
              "extractedItems": [
                {
                  "title": "string",
                  "category": "string",
                  "subCategory": "string",
                  "summary": "string",
                  "keyData": "string",
                  "url": "string",
                  "tags": ["string"]
                }
              ]
            }
            """;

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqSummaryService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public AiSummaryResult summarize(String transcript) {
        try {
            String apiKey = appProperties.getOpenai().getApiKey(); // Reuse config for Groq key
            String model = appProperties.getOpenai().getSummaryModel();
            if (model == null || model.isBlank()
                    || model.equals("gpt-4o")
                    || model.equals("llama-3.3-70b-versatile")) {
                model = DEFAULT_MODEL;
            }

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "Transcript:\n" + transcript)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2048,
                    "response_format", Map.of("type", "json_object")
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_CHAT_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var json = objectMapper.readTree(response.body());
                String content = json.path("choices").get(0).path("message").path("content").asText();
                log.info("Groq summary completed using model={}", model);
                AiSummaryResult result = objectMapper.readValue(content, AiSummaryResult.class);
                ensureExtractedItems(result);
                return result;
            } else {
                log.error("Groq summary failed: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("Groq summary failed with status " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Groq summary error", e);
        }
    }

    private void ensureExtractedItems(AiSummaryResult result) {
        if (result.getExtractedItems() == null || result.getExtractedItems().isEmpty()) {
            List<ExtractedKnowledgeItemDto> fallbackList = new ArrayList<>();
            if (result.getToolsOrProducts() != null && !result.getToolsOrProducts().isEmpty()) {
                for (String tool : result.getToolsOrProducts()) {
                    fallbackList.add(ExtractedKnowledgeItemDto.builder()
                            .title(tool)
                            .category("Tools")
                            .subCategory("AI & Automation")
                            .summary(result.getSummary())
                            .keyData(result.getKeyPoints() != null && !result.getKeyPoints().isEmpty() ? result.getKeyPoints().get(0) : null)
                            .tags(result.getTopics())
                            .build());
                }
            } else {
                fallbackList.add(ExtractedKnowledgeItemDto.builder()
                        .title(result.getTitle() != null && !result.getTitle().isBlank() ? result.getTitle() : "Reel Insight")
                        .category("Insights & Resources")
                        .subCategory(result.getTopics() != null && !result.getTopics().isEmpty() ? result.getTopics().get(0) : "General")
                        .summary(result.getSummary())
                        .keyData(result.getKeyPoints() != null ? String.join("; ", result.getKeyPoints()) : null)
                        .tags(result.getTopics())
                        .build());
            }
            result.setExtractedItems(fallbackList);
        }
    }
}
