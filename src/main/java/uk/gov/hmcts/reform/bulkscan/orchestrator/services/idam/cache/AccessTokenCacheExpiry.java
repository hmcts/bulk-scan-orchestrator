package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AccessTokenCacheExpiry implements Expiry<String, CachedIdamToken> {

    private final long refreshTokenBeforeExpiry;

    public AccessTokenCacheExpiry(
        @Value("${idam.client.cache.refresh-before-expire-in-sec}") long refreshTokenBeforeExpiry
    ) {
        this.refreshTokenBeforeExpiry = refreshTokenBeforeExpiry;
    }

    @Override
    public long expireAfterCreate(
        @NonNull String jurisdiction,
        @NonNull CachedIdamToken cachedIdamToken,
        long currentTime
    ) {
        return TimeUnit.SECONDS.toNanos(cachedIdamToken.expiresIn - refreshTokenBeforeExpiry);
    }

    @Override
    public long expireAfterUpdate(
        @NonNull String jurisdiction,
        @NonNull CachedIdamToken cachedIdamToken,
        long currentTime,
        @NonNegative long currentDuration
    ) {
        return currentDuration;
    }

    @Override
    public long expireAfterRead(
        @NonNull String jurisdiction,
        @NonNull CachedIdamToken cachedIdamToken,
        long currentTime,
        @NonNegative long currentDuration
    ) {
        return currentDuration;
    }
}