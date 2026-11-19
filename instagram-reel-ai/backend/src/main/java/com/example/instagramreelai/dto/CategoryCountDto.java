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
public class CategoryCountDto {
    private String name;
    private long count;
    private List<SubCategoryCountDto> subCategories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubCategoryCountDto {
        private String name;
        private long count;
    }
}
