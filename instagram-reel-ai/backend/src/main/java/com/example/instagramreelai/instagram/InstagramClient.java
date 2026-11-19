package com.example.instagramreelai.instagram;

import java.util.Optional;

/**
 * Abstraction over Instagram/Meta messaging API.
 * Implementations: MetaInstagramClient (real API) and MockInstagramClient (testing).
 */
public interface InstagramClient {

    /**
     * Attempt to resolve a media download URL from an attachment/media reference.
     * Returns empty if the media is not accessible via the authorized API.
     */
    Optional<String> resolveMediaUrl(String mediaId, String accessToken);

    /**
     * Send a text reply from the connected Instagram account to a user.
     */
    boolean sendReply(String accountId, String recipientId, String message, String accessToken);

    /**
     * Verify webhook subscription challenge.
     */
    boolean verifyWebhook(String verifyToken, String challenge);
}
