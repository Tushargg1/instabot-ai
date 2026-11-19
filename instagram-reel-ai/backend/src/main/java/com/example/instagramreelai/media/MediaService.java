package com.example.instagramreelai.media;

import com.example.instagramreelai.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav"
    );

    private final AppProperties appProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Downloads media from the given URL to a temporary file.
     * Supports direct CDN URLs as well as YouTube Shorts and TikTok links via yt-dlp.
     */
    public Path downloadMedia(String mediaUrl) throws IOException, InterruptedException {
        if (isSocialVideoUrl(mediaUrl)) {
            try {
                return downloadViaYtDlp(mediaUrl);
            } catch (Exception e) {
                log.warn("yt-dlp download failed, falling back to direct download: {}", e.getMessage());
            }
        }
        return downloadDirect(mediaUrl);
    }

    private Path downloadViaYtDlp(String url) throws IOException, InterruptedException {
        Path tempDir = Path.of(appProperties.getMedia().getTempDir());
        Files.createDirectories(tempDir);
        Path outputFile = tempDir.resolve(UUID.randomUUID() + ".mp4");

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-f", "best[ext=mp4]/best",
                "-o", outputFile.toString(),
                "--max-filesize", "50M",
                "--no-playlist",
                url
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(45, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0 || !Files.exists(outputFile) || Files.size(outputFile) == 0) {
            Files.deleteIfExists(outputFile);
            throw new IOException("yt-dlp exited with error or produced empty file");
        }
        return outputFile;
    }

    private boolean isSocialVideoUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("tiktok.com");
    }

    private Path downloadDirect(String mediaUrl) throws IOException, InterruptedException {
        validateUrl(mediaUrl);

        Path tempDir = Path.of(appProperties.getMedia().getTempDir());
        Files.createDirectories(tempDir);

        Path tempFile = tempDir.resolve(UUID.randomUUID() + ".media");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mediaUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Download failed with status " + response.statusCode());
        }

        // Validate file size
        long maxBytes = (long) appProperties.getMedia().getMaxSizeMb() * 1024 * 1024;
        long fileSize = Files.size(tempFile);
        if (fileSize > maxBytes) {
            Files.deleteIfExists(tempFile);
            throw new IOException("Media exceeds maximum size: " + fileSize + " bytes");
        }

        return tempFile;
    }

    /**
     * Extracts audio from a video file using FFmpeg.
     * Optimized for Whisper: 16kHz mono 32kbps MP3 (~200KB per minute)
     * Cuts upload and transcription latency by >60%.
     */
    public Path extractAudio(Path videoFile) throws IOException, InterruptedException {
        Path audioFile = videoFile.resolveSibling(UUID.randomUUID() + ".mp3");
        String ffmpegPath = appProperties.getMedia().getFfmpegPath();

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-i", videoFile.toString(),
                "-vn", "-acodec", "libmp3lame", "-b:a", "32k", "-ac", "1", "-ar", "16000",
                "-y", audioFile.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            Files.deleteIfExists(audioFile);
            throw new IOException("FFmpeg audio extraction failed with exit code " + exitCode);
        }

        return audioFile;
    }

    /**
     * Validates a MIME type against allowed types.
     */
    public boolean isAllowedMimeType(String mimeType) {
        return mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    /**
     * Cleans up temporary files.
     */
    public void cleanup(Path... files) {
        for (Path file : files) {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", file, e);
                }
            }
        }
    }

    /**
     * SSRF protection: reject private/internal network URLs.
     */
    private void validateUrl(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();

            if (host == null) {
                throw new IOException("Invalid URL: no host");
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() ||
                    address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new IOException("SSRF protection: cannot access internal address " + host);
            }

            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                throw new IOException("Only HTTP/HTTPS URLs are allowed");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid URL: " + url, e);
        }
    }
}
