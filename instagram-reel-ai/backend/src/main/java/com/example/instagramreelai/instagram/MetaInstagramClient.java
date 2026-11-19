package com.example.instagramreelai.instagram;

import com.example.instagramreelai.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Real Meta/Instagram Graph API client.
 * Only active when 'prod' profile is used and Instagram is configured.
 *
 * NOTE: The exact API endpoints and fields must be verified against
 * the current Meta developer documentation. This implementation follows
 * the documented Instagram Messaging API patterns.
 */
@Slf4j
@Component
@Profile("!dev")
public class MetaInstagramClient implements InstagramClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MetaInstagramClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    private String graphApiBase() {
        AppProperties.Instagram config = appProperties.getInstagram();
        return config.getGraphApiBase() + "/" + config.getGraphApiVersion();
    }

    @Override
    public Optional<String> resolveMediaUrl(String mediaId, String accessToken) {
        try {
            String url = graphApiBase() + "/" + mediaId + "?fields=media_url";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String mediaUrl = json.path("media_url").asText(null);
                return Optional.ofNullable(mediaUrl);
            } else {
                log.warn("Failed to resolve media URL for mediaId={}, status={}", mediaId, response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error resolving media URL for mediaId={}", mediaId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean sendReply(String accountId, String recipientId, String message, String accessToken) {
        try {
            String url = graphApiBase() + "/" + accountId + "/messages";

            String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("recipient", new java.util.HashMap<>() {{ put("id", recipientId); }});
                put("message", new java.util.HashMap<>() {{ put("text", message); }});
            }});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return true;
            } else {
                log.warn("Failed to send reply to recipientId={}, status={}, body={}",
                        recipientId, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending reply to recipientId={}", recipientId, e);
            return false;
        }
    }

    @Override
    public Optional<InstagramUserProfile> getUserProfile(String igsid, String accessToken) {
        if (igsid == null || igsid.isBlank() || accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = graphApiBase() + "/" + igsid + "?fields=name,username,profile_pic,is_user_follow_business,is_business_follow_user";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                InstagramUserProfile profile = InstagramUserProfile.builder()
                        .id(json.path("id").asText(igsid))
                        .username(json.path("username").asText(null))
                        .name(json.path("name").asText(null))
                        .profilePic(json.path("profile_pic").asText(null))
                        .isUserFollowBusiness(json.has("is_user_follow_business") ? json.path("is_user_follow_business").asBoolean() : null)
                        .isBusinessFollowUser(json.has("is_business_follow_user") ? json.path("is_business_follow_user").asBoolean() : null)
                        .build();
                return Optional.of(profile);
            } else {
                log.warn("Failed to fetch user profile for igsid={}, status={}, body={}", igsid, response.statusCode(), response.body());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error fetching user profile for igsid={}", igsid, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean verifyWebhook(String verifyToken, String challenge) {
        return appProperties.getInstagram().getWebhookVerifyToken().equals(verifyToken);
    }
}
