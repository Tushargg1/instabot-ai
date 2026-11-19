package com.example.instagramreelai.ai;

import java.nio.file.Path;

/**
 * Transcribes audio to text.
 */
public interface TranscriptionService {
    TranscriptionResult transcribe(Path audioFile);

    record TranscriptionResult(String text, String language, Double durationSeconds) {}
}
