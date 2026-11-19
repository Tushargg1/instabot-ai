package com.example.instagramreelai.job;

import java.util.Arrays;
import java.util.Optional;

/** Persisted job kinds. Parsing is deliberately non-throwing so corrupt rows can be failed safely. */
public enum JobType {
    AI_PIPELINE,
    REPLY,
    RESOURCE_DETECTION,
    RESOURCE_REFRESH,
    MANUAL_ACTION;

    public static Optional<JobType> fromPersisted(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(type -> type.name().equals(value)).findFirst();
    }
}
