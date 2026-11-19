package com.example.instagramreelai.dto;

import com.example.instagramreelai.entity.DmReplyRule;
import lombok.Data;

@Data
public class DmReplyRuleRequest {
    private String name;
    private DmReplyRule.MatchType matchType;
    private String matchValue;    // null/empty is fine for matchType=ALL
    private String replyText;
    private Integer priority;
    private Boolean enabled;
}
