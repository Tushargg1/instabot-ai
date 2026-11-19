package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.UserProfileDto;
import com.example.instagramreelai.dto.UserSettingsDto;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserSettingsService settingsService;
    private final AppUserRepository userRepository;

    /**
     * Get current user profile with settings status.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(Authentication auth) {
        AppUser user = getUser(auth);
        boolean hasOpenAi = settingsService.hasKey(user.getId(), UserSettingsService.OPENAI_API_KEY);
        boolean hasInstagram = settingsService.hasKey(user.getId(), UserSettingsService.INSTAGRAM_ACCESS_TOKEN);

        return ResponseEntity.ok(UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .hasOpenAiKey(hasOpenAi)
                .hasInstagramConfig(hasInstagram)
                .build());
    }

    /**
     * Get all user settings (sensitive values masked).
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getSettings(Authentication auth) {
        AppUser user = getUser(auth);
        return ResponseEntity.ok(settingsService.getAllSettingsMasked(user.getId()));
    }

    /**
     * Save/update user settings (API keys, Instagram config).
     */
    @PutMapping
    public ResponseEntity<Map<String, String>> saveSettings(
            @RequestBody UserSettingsDto dto, Authentication auth) {
        AppUser user = getUser(auth);

        Map<String, String> settings = new LinkedHashMap<>();
        if (dto.getOpenaiApiKey() != null) settings.put(UserSettingsService.OPENAI_API_KEY, dto.getOpenaiApiKey());
        if (dto.getOpenaiTranscriptionModel() != null) settings.put(UserSettingsService.OPENAI_TRANSCRIPTION_MODEL, dto.getOpenaiTranscriptionModel());
        if (dto.getOpenaiSummaryModel() != null) settings.put(UserSettingsService.OPENAI_SUMMARY_MODEL, dto.getOpenaiSummaryModel());
        if (dto.getInstagramAppId() != null) settings.put(UserSettingsService.INSTAGRAM_APP_ID, dto.getInstagramAppId());
        if (dto.getInstagramAppSecret() != null) settings.put(UserSettingsService.INSTAGRAM_APP_SECRET, dto.getInstagramAppSecret());
        if (dto.getInstagramAccessToken() != null) settings.put(UserSettingsService.INSTAGRAM_ACCESS_TOKEN, dto.getInstagramAccessToken());
        if (dto.getInstagramAccountId() != null) settings.put(UserSettingsService.INSTAGRAM_ACCOUNT_ID, dto.getInstagramAccountId());
        if (dto.getInstagramWebhookVerifyToken() != null) settings.put(UserSettingsService.INSTAGRAM_WEBHOOK_VERIFY_TOKEN, dto.getInstagramWebhookVerifyToken());

        settingsService.saveSettings(user.getId(), settings);

        return ResponseEntity.ok(settingsService.getAllSettingsMasked(user.getId()));
    }

    /**
     * Delete a specific setting.
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key, Authentication auth) {
        AppUser user = getUser(auth);
        settingsService.deleteSetting(user.getId(), key);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update user profile (email, displayName).
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @RequestBody Map<String, String> body, Authentication auth) {
        AppUser user = getUser(auth);
        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("displayName")) user.setDisplayName(body.get("displayName"));
        userRepository.save(user);

        boolean hasOpenAi = settingsService.hasKey(user.getId(), UserSettingsService.OPENAI_API_KEY);
        boolean hasInstagram = settingsService.hasKey(user.getId(), UserSettingsService.INSTAGRAM_ACCESS_TOKEN);

        return ResponseEntity.ok(UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .hasOpenAiKey(hasOpenAi)
                .hasInstagramConfig(hasInstagram)
                .build());
    }

    private AppUser getUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
