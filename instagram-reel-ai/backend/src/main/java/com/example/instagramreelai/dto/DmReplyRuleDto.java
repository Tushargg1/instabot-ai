package com.example.instagramreelai.dto;

import com.example.instagramreelai.entity.DmReplyRule;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DmReplyRuleDto {
    private UUID id;
    private String name;
    private DmReplyRule.MatchType matchType;
    private String matchValue;
    private String replyText;
    private Integer priority;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public static DmReplyRuleDto from(DmReplyRule rule) {
        return DmReplyRuleDto.builder()
                .id(rule.getId())
                .name(rule.getName())
                .matchType(rule.getMatchType())
                .matchValue(rule.getMatchValue())
                .replyText(rule.getReplyText())
                .priority(rule.getPriority())
                .enabled(rule.getEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
