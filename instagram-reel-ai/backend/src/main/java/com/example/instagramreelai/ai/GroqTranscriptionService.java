package com.example.instagramreelai.ai;

import com.example.instagramreelai.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Groq API transcription using Whisper Large v3 Turbo.
 * Free tier: ~30 req/min, 25MB max file size.
 * API is OpenAI-compatible (same multipart format).
 *
 * Get your free key at: https://console.groq.com
 */
@Slf4j
@Service
@Profile("!dev")
public class GroqTranscriptionService implements TranscriptionService {

    private static final String GROQ_TRANSCRIPTION_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String DEFAULT_MODEL = "whisper-large-v3-turbo";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GroqTranscriptionService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public TranscriptionResult transcribe(Path audioFile) {
        try {
            String apiKey = appProperties.getOpenai().getApiKey(); // Reuse same config field for Groq key
            String model = appProperties.getOpenai().getTranscriptionModel();
            if (model == null || model.isBlank() || model.equals("whisper-1")) {
                model = DEFAULT_MODEL;
            }

            String boundary = "----GroqBoundary" + System.currentTimeMillis();
            byte[] fileBytes = Files.readAllBytes(audioFile);
            String fileName = audioFile.getFileName().toString();

            // Determine content type
            String contentType = "audio/mpeg";
            if (fileName.endsWith(".wav")) contentType = "audio/wav";
            else if (fileName.endsWith(".mp4") || fileName.endsWith(".m4a")) contentType = "audio/mp4";
            else if (fileName.endsWith(".ogg")) contentType = "audio/ogg";

            String bodyStart = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"model\"\r\n\r\n" +
                    model + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"response_format\"\r\n\r\n" +
                    "verbose_json\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                    "Content-Type: " + contentType + "\r\n\r\n";
            String bodyEnd = "\r\n--" + boundary + "--\r\n";

            byte[] startBytes = bodyStart.getBytes();
            byte[] endBytes = bodyEnd.getBytes();
            byte[] fullBody = new byte[startBytes.length + fileBytes.length + endBytes.length];
            System.arraycopy(startBytes, 0, fullBody, 0, startBytes.length);
            System.arraycopy(fileBytes, 0, fullBody, startBytes.length, fileBytes.length);
            System.arraycopy(endBytes, 0, fullBody, startBytes.length + fileBytes.length, endBytes.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_TRANSCRIPTION_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fullBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String text = json.path("text").asText();
                String language = json.path("language").asText("en");
                double duration = json.path("duration").asDouble(0);
                log.info("Groq transcription completed: {}s, language={}", duration, language);
                return new TranscriptionResult(text, language, duration);
            } else {
                log.error("Groq transcription failed: status={}, body={}", response.statusCode(), response.body());
                throw new RuntimeException("Groq transcription failed with status " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Groq transcription error", e);
        }
    }
}
