package com.example.instagramreelai.ai;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
            You are the summarization engine for a personal Instagram Reel knowledge base.
            Analyze the supplied transcript.
            Rules:
            - Do not invent facts.
            - Separate what the speaker claims from what can be confidently inferred.
            - Preserve important technical names, products, people and companies.
            - If the transcript is incomplete or noisy, say so.
            - Do not treat promotional claims as verified facts.
            - Identify claims that should be independently verified.
            - Keep the summary concise but useful (80-180 words).
            - Prefer concrete information over generic statements.
            
            Return ONLY valid JSON matching this exact schema (no markdown, no explanation):
            {"title":"string","summary":"string","keyPoints":["string"],"actionItems":["string"],"topics":["string"],"toolsOrProducts":["string"],"peopleOrCompanies":["string"],"claimsToVerify":["string"],"language":"string","confidence":0.0}
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
                return objectMapper.readValue(content, AiSummaryResult.class);
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
}
