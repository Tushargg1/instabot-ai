package com.example.instagramreelai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ManualActionClaimRequest(
        @Min(30) @Max(3600) Integer leaseSeconds
) {
}
