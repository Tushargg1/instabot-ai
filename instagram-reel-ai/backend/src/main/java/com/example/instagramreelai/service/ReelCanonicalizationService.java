package com.example.instagramreelai.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReelCanonicalizationService {
    private static final Pattern INSTAGRAM_PATH = Pattern.compile("^/(?:reel|reels|p)/([A-Za-z0-9_-]+)");

    public Optional<CanonicalReel> canonicalize(String mediaId, String sourceUrl) {
        if (hasText(mediaId)) {
            return Optional.of(new CanonicalReel("instagram:media:" + mediaId.trim(), sourceUrl));
        }
        if (!hasText(sourceUrl)) return Optional.empty();
        try {
            URI uri = URI.create(sourceUrl.trim());
            String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
            Matcher matcher = INSTAGRAM_PATH.matcher(Optional.ofNullable(uri.getPath()).orElse(""));
            if ((host.equals("instagram.com") || host.endsWith(".instagram.com")) && matcher.find()) {
                String shortcode = matcher.group(1);
                return Optional.of(new CanonicalReel("instagram:shortcode:" + shortcode,
                        "https://www.instagram.com/reel/" + shortcode + "/"));
            }
            String scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
            if ((scheme.equals("https") || scheme.equals("http")) && !host.isBlank()) {
                URI normalized = new URI(scheme, null, host, uri.getPort(), uri.getPath(), null, null);
                String stableUrl = normalized.toString();
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(stableUrl.getBytes(StandardCharsets.UTF_8));
                return Optional.of(new CanonicalReel("external:url:" + HexFormat.of().formatHex(digest), stableUrl));
            }
        } catch (Exception ignored) {
            // Unsupported URL is handled by the signed web-form fallback in a later stage.
        }
        return Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CanonicalReel(String key, String normalizedUrl) {}
}
