package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class WebhookSignatureService {
    private final AppProperties appProperties;

    public boolean isValid(String rawPayload, String signature) {
        AppProperties.Instagram config = appProperties.getInstagram();
        if (!config.isWebhookSignatureRequired()) return true;
        if (signature == null || !signature.startsWith("sha256=")
                || config.getAppSecret() == null || config.getAppSecret().isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(
                    mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify webhook signature", exception);
        }
    }
}
