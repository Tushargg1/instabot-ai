package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverProspectsRequest {
    private String flowTag;
    private String criteria;
    private Integer count;
}
