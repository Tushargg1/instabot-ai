package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramOAuthService {

    private final AppProperties appProperties;
    private final InstagramOAuthStateService stateService;
    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isConfigured() {
        AppProperties.Instagram config = appProperties.getInstagram();
        return hasText(config.getAppId()) && hasText(config.getAppSecret())
                && hasText(config.getOauthRedirectUri());
    }

    public String createAuthorizationUrl(AppUser user) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Instagram OAuth is not configured by the application operator");
        }
        AppProperties.Instagram config = appProperties.getInstagram();
        String state = stateService.create(user, config.getOauthStateTtlMinutes());
        return UriComponentsBuilder.fromUriString(config.getOauthAuthorizeUrl())
                .queryParam("client_id", config.getAppId())
                .queryParam("redirect_uri", config.getOauthRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", config.getOauthScopes())
                .queryParam("state", state)
                .queryParam("force_reauth", "true")
                .build().encode().toUriString();
    }

    public InstagramAccount completeAuthorization(String code, String state) {
        if (!isConfigured()) {
            throw new IllegalStateException("Instagram OAuth is not configured");
        }
        if (!hasText(code) || !hasText(state)) {
            throw new IllegalArgumentException("Instagram did not return an authorization code and state");
        }

        AppUser user = stateService.consume(state);
        ShortLivedToken shortToken = exchangeCode(code);
        LongLivedToken longToken = exchangeForLongLivedToken(shortToken.accessToken());
        Profile profile = fetchProfile(shortToken.userId(), longToken.accessToken());
        return saveConnection(user, profile, longToken);
    }

    public Optional<InstagramAccount> getConnection(UUID ignoredUserId) {
        return accountRepository.findFirstByActiveTrueOrderByUpdatedAtDesc();
    }

    public void disconnect(UUID ignoredUserId) {
        List<InstagramAccount> accounts = accountRepository.findByActiveTrue();
        for (InstagramAccount account : accounts) {
            account.setActive(false);
            account.setConnectionStatus("DISCONNECTED");
            account.setAccessTokenEncrypted(null);
            account.setTokenExpiresAt(null);
        }
        accountRepository.saveAll(accounts);
    }

    private ShortLivedToken exchangeCode(String code) {
        AppProperties.Instagram config = appProperties.getInstagram();
        String body = formEncode(Map.of(
                "client_id", config.getAppId(),
                "client_secret", config.getAppSecret(),
                "grant_type", "authorization_code",
                "redirect_uri", config.getOauthRedirectUri(),
                "code", code));
        JsonNode json = send(HttpRequest.newBuilder(URI.create(config.getOauthTokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), "authorization code exchange");
        return new ShortLivedToken(requiredText(json, "access_token"), requiredText(json, "user_id"));
    }

    private LongLivedToken exchangeForLongLivedToken(String shortLivedToken) {
        AppProperties.Instagram config = appProperties.getInstagram();
        String url = UriComponentsBuilder.fromUriString(config.getGraphApiBase() + "/access_token")
                .queryParam("grant_type", "ig_exchange_token")
                .queryParam("client_secret", config.getAppSecret())
                .queryParam("access_token", shortLivedToken)
                .build().encode().toUriString();
        JsonNode json = send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                "long-lived token exchange");
        long expiresIn = json.path("expires_in").asLong(3600);
        return new LongLivedToken(requiredText(json, "access_token"), Instant.now().plusSeconds(expiresIn));
    }

    private Profile fetchProfile(String userId, String accessToken) {
        AppProperties.Instagram config = appProperties.getInstagram();
        String url = UriComponentsBuilder.fromUriString(config.getGraphApiBase() + "/"
                        + config.getGraphApiVersion() + "/" + userId)
                .queryParam("fields", "user_id,username,name,profile_picture_url")
                .build().encode().toUriString();
        JsonNode json = send(HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build(), "Instagram profile lookup");
        String accountId = json.path("user_id").asText(json.path("id").asText(userId));
        return new Profile(accountId, json.path("username").asText(""),
                json.path("name").asText(""), json.path("profile_picture_url").asText(""));
    }

    private InstagramAccount saveConnection(AppUser user, Profile profile, LongLivedToken token) {
        InstagramAccount account = accountRepository.findByInstagramAccountId(profile.accountId())
                .orElseGet(InstagramAccount::new);
        if (account.getUser() != null && !account.getUser().getId().equals(user.getId())) {
            throw new IllegalStateException("This Instagram account is already connected to another user");
        }
        account.setInstagramAccountId(profile.accountId());
        account.setUser(user);
        account.setUsername(profile.username());
        account.setDisplayName(hasText(profile.displayName()) ? profile.displayName() : profile.username());
        account.setProfilePictureUrl(profile.profilePictureUrl());
        account.setAccessTokenEncrypted(encryptionService.encrypt(token.accessToken()));
        account.setTokenExpiresAt(token.expiresAt());
        account.setScopes(appProperties.getInstagram().getOauthScopes());
        account.setConnectionStatus("MAIN_CONNECTED");
        account.setActive(true);

        List<InstagramAccount> previousMainAccounts = accountRepository.findByActiveTrue();
        previousMainAccounts.stream()
                .filter(existing -> !existing.getInstagramAccountId().equals(profile.accountId()))
                .forEach(existing -> {
                    existing.setActive(false);
                    existing.setConnectionStatus("REPLACED");
                    existing.setAccessTokenEncrypted(null);
                });
        accountRepository.saveAll(previousMainAccounts);
        return accountRepository.save(account);
    }

    private JsonNode send(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = json.path("error_message").asText(
                        json.path("error").path("message").asText("Meta API request failed"));
                throw new IllegalStateException(operation + " failed: " + message);
            }
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " was interrupted", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException(operation + " failed", e);
        }
    }

    private String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String requiredText(JsonNode json, String field) {
        String value = json.path(field).asText();
        if (!hasText(value)) throw new IllegalStateException("Meta response is missing " + field);
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ShortLivedToken(String accessToken, String userId) {}
    private record LongLivedToken(String accessToken, Instant expiresAt) {}
    private record Profile(String accountId, String username, String displayName,
                           String profilePictureUrl) {}
}
