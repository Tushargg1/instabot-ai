package com.example.instagramreelai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserProfileDto {
    private UUID id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private boolean hasOpenAiKey;
    private boolean hasInstagramConfig;
}
