package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.UserSetting;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Manages per-user settings including API keys (encrypted) and preferences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    // Known setting keys
    public static final String OPENAI_API_KEY = "openai_api_key";
    public static final String OPENAI_TRANSCRIPTION_MODEL = "openai_transcription_model";
    public static final String OPENAI_SUMMARY_MODEL = "openai_summary_model";
    public static final String INSTAGRAM_APP_ID = "instagram_app_id";
    public static final String INSTAGRAM_APP_SECRET = "instagram_app_secret";
    public static final String INSTAGRAM_ACCESS_TOKEN = "instagram_access_token";
    public static final String INSTAGRAM_ACCOUNT_ID = "instagram_account_id";
    public static final String INSTAGRAM_WEBHOOK_VERIFY_TOKEN = "instagram_webhook_verify_token";

    // Sensitive keys that get encrypted
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            OPENAI_API_KEY, INSTAGRAM_APP_SECRET, INSTAGRAM_ACCESS_TOKEN
    );

    private final UserSettingRepository settingRepository;
    private final AppUserRepository userRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public void saveSetting(UUID userId, String key, String value) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean isSensitive = SENSITIVE_KEYS.contains(key);
        String storedValue = isSensitive ? encryptionService.encrypt(value) : value;

        UserSetting setting = settingRepository.findByUserIdAndSettingKey(userId, key)
                .orElse(UserSetting.builder()
                        .user(user)
                        .settingKey(key)
                        .build());

        setting.setSettingValue(storedValue);
        setting.setEncrypted(isSensitive);
        settingRepository.save(setting);
    }

    @Transactional
    public void saveSettings(UUID userId, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                saveSetting(userId, entry.getKey(), entry.getValue());
            }
        }
    }

    public Optional<String> getSetting(UUID userId, String key) {
        return settingRepository.findByUserIdAndSettingKey(userId, key)
                .map(setting -> {
                    if (setting.getEncrypted()) {
                        return encryptionService.decrypt(setting.getSettingValue());
                    }
                    return setting.getSettingValue();
                });
    }

    /**
     * Returns all settings for a user.
     * Sensitive values are masked (only last 4 chars shown).
     */
    public Map<String, String> getAllSettingsMasked(UUID userId) {
        List<UserSetting> settings = settingRepository.findByUserId(userId);
        Map<String, String> result = new LinkedHashMap<>();

        for (UserSetting setting : settings) {
            if (setting.getEncrypted()) {
                String decrypted = encryptionService.decrypt(setting.getSettingValue());
                result.put(setting.getSettingKey(), maskValue(decrypted));
            } else {
                result.put(setting.getSettingKey(), setting.getSettingValue());
            }
        }
        return result;
    }

    /**
     * Returns whether a specific key has been configured.
     */
    public boolean hasKey(UUID userId, String key) {
        return settingRepository.findByUserIdAndSettingKey(userId, key).isPresent();
    }

    @Transactional
    public void deleteSetting(UUID userId, String key) {
        settingRepository.deleteByUserIdAndSettingKey(userId, key);
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
