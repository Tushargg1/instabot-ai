package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingPlaybookRequest {
    private String title;
    private String tag;
    private String content;
    private String pdfFilename;
    private Boolean strictGuidelines;
}
