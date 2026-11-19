package com.example.instagramreelai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualActionFailRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
