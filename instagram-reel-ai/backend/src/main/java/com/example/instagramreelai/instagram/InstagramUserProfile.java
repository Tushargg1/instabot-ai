package com.example.instagramreelai.instagram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramUserProfile {
    private String id;
    private String username;
    private String name;
    private String profilePic;
    private Boolean isUserFollowBusiness;
    private Boolean isBusinessFollowUser;
}
