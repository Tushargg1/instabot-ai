package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {
    List<UserSetting> findByUserId(UUID userId);
    Optional<UserSetting> findByUserIdAndSettingKey(UUID userId, String settingKey);
    void deleteByUserIdAndSettingKey(UUID userId, String settingKey);
}
