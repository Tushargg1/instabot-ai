package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.job.JobStatus;
import com.example.instagramreelai.repository.ReelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CanonicalReelStore {
    private final ReelRepository reelRepository;
    private final ReelCacheService cacheService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reel resolveOrCreate(ReelCanonicalizationService.CanonicalReel canonical,
                                String mediaId, InstagramAccount account) {
        var cached = cacheService.getReelId(canonical.key()).flatMap(reelRepository::findById);
        if (cached.isPresent()) return cached.get();

        var existing = reelRepository.findByCanonicalKey(canonical.key());
        if (existing.isPresent()) {
            cacheService.putReelId(canonical.key(), existing.get().getId());
            return existing.get();
        }

        Reel created = reelRepository.saveAndFlush(Reel.builder()
                .canonicalKey(canonical.key())
                .instagramAccount(account)
                .user(account.getUser())
                .sourceUrl(canonical.normalizedUrl())
                .externalMediaId(mediaId)
                .processingStatus(JobStatus.RECEIVED)
                .requestCount(0L)
                .build());
        cacheService.putReelId(canonical.key(), created.getId());
        return created;
    }
}
