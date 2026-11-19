package com.example.instagramreelai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
@Primary
@Profile("dev")
public class MockTranscriptionService implements TranscriptionService {

    @Override
    public TranscriptionResult transcribe(Path audioFile) {
        log.info("[MOCK] Transcribing audio file: {}", audioFile.getFileName());
        String mockTranscript = "This is a mock transcription of the Instagram Reel. " +
                "The speaker discusses productivity tips including time blocking, " +
                "the Pomodoro technique, and the importance of deep work. " +
                "They recommend the book Deep Work by Cal Newport.";
        return new TranscriptionResult(mockTranscript, "en", 45.0);
    }
}
