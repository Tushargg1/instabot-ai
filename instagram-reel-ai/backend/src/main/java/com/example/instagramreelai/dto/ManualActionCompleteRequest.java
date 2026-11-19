package com.example.instagramreelai.dto;

import jakarta.validation.constraints.Size;

public record ManualActionCompleteRequest(
        @Size(max = 10000) String result
) {
}
