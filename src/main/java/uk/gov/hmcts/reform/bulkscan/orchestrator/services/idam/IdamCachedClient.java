package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.concurrent.TimeUnit;

@Service
public class IdamCachedClient {

    public static final String BEARER_AUTH_TYPE = "Bearer ";
    public static final String EXPIRES_IN = "expires_in";

    private static Cache<String, CachedIdamToken> accessTokenCache;

    private final IdamClient idamClient;
    private final JurisdictionToUserMapping users;
    private long refreshTokenBeforeExpiry;

    public IdamCachedClient(
            IdamClient idamClient,
            JurisdictionToUserMapping users,
            @Value("${idam.client.cache.refresh-before-expire-in-sec}") long refreshTokenBeforeExpiry
    ) {
        this.idamClient = idamClient;
        this.users = users;
        this.refreshTokenBeforeExpiry = refreshTokenBeforeExpiry;
        this.accessTokenCache = Caffeine.newBuilder()
                .expireAfter(new AccessTokenCacheExpiry())
                .build();
    }

    public String getAccessToken(String jurisdiction) {
        CachedIdamToken cachedIdamToken = this.accessTokenCache.get(jurisdiction, j -> retrieveToken(j));
        return cachedIdamToken.token;
    }

    private CachedIdamToken retrieveToken(String jurisdiction) {
        Credential user = users.getUser(jurisdiction);

        String tokenWithBearer = idamClient.authenticateUser(
                user.getUsername(),
                user.getPassword()
        );

        return new CachedIdamToken(tokenWithBearer, stripExpiryFromBearerToken(tokenWithBearer));
    }

    private long stripExpiryFromBearerToken(String tokenWithBearer) {

        DecodedJWT jwt;
        try {
            jwt = JWT.decode(tokenWithBearer.replace(BEARER_AUTH_TYPE, ""));
        } catch (Exception ex) {
            throw new InvalidTokenException("Idam token decoding error.", ex);
        }

        Claim expires = jwt.getClaim(EXPIRES_IN);
        if (expires.isNull()) {
            throw new InvalidTokenException("Invalid idam token, 'expires_in' is missing.");
        }

        return expires.asLong();
    }

    private class AccessTokenCacheExpiry implements Expiry<String, CachedIdamToken> {

        @Override
        public long expireAfterCreate(
                @NonNull String key,
                @NonNull CachedIdamToken tokenResp,
                long currentTime
        ) {
            return TimeUnit.SECONDS.toNanos(tokenResp.expiresIn - refreshTokenBeforeExpiry);
        }

        @Override
        public long expireAfterUpdate(
                @NonNull String key,
                @NonNull CachedIdamToken value,
                long currentTime,
                @NonNegative long currentDuration
        ) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(
                @NonNull String key,
                @NonNull CachedIdamToken value,
                long currentTime,
                @NonNegative long currentDuration
        ) {
            return currentDuration;
        }
    }
}
