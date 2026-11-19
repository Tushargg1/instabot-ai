package com.example.instagramreelai.media;

import com.example.instagramreelai.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Mock media service for dev that creates dummy files instead of downloading.
 */
@Slf4j
@Service
@Primary
@Profile("dev")
public class MockMediaService extends MediaService {

    public MockMediaService(AppProperties appProperties) {
        super(appProperties);
    }

    @Override
    public Path downloadMedia(String mediaUrl) throws IOException {
        log.info("[MOCK] Simulating media download from: {}", mediaUrl);
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "instagram-reel-ai");
        Files.createDirectories(tempDir);
        Path tempFile = tempDir.resolve(UUID.randomUUID() + ".mp4");
        Files.writeString(tempFile, "MOCK_VIDEO_CONTENT");
        return tempFile;
    }

    @Override
    public Path extractAudio(Path videoFile) throws IOException {
        log.info("[MOCK] Simulating audio extraction from: {}", videoFile.getFileName());
        Path audioFile = videoFile.resolveSibling(UUID.randomUUID() + ".mp3");
        Files.writeString(audioFile, "MOCK_AUDIO_CONTENT");
        return audioFile;
    }
}
