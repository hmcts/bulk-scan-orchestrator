package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheWriter;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

@Service
public class IdamCachedClient {

    private static final Logger log = LoggerFactory.getLogger(IdamCachedClient.class);
    public static final String BEARER_AUTH_TYPE = "Bearer ";
    public static final String EXPIRES_IN = "expires_in";

    private static Cache<String, CachedIdamToken> accessTokenCache;
    private static Cache<String, UserDetails> userDetailsCache;

    private final IdamClient idamClient;
    private final JurisdictionToUserMapping users;

    public IdamCachedClient(
        IdamClient idamClient,
        JurisdictionToUserMapping users,
        AccessTokenCacheExpiry accessTokenCacheExpiry
    ) {
        this.idamClient = idamClient;
        this.users = users;
        this.accessTokenCache = Caffeine.newBuilder()
            .expireAfter(accessTokenCacheExpiry)
            .writer(new CacheWriter<String, CachedIdamToken>() {
                @Override
                public void write(@NonNull String key, @NonNull CachedIdamToken value) {
                    throw new UnsupportedOperationException("Cache put() or replace() not supported.");
                }

                @Override
                public void delete(@NonNull String jurisdiction, @Nullable CachedIdamToken cachedIdamToken,
                    @NonNull RemovalCause cause) {
                    log.info("On access token removal invalidate user details. "
                            + "Access token removed for jurisdiction: {}, cause: {} ",
                        jurisdiction,
                        cause);
                    if (cachedIdamToken != null) {
                        userDetailsCache.invalidate(cachedIdamToken.accessToken);
                    }
                }

                }
                )
            .build();

        this.userDetailsCache =  Caffeine.newBuilder()
            .maximumSize(200)
            .build();
    }

    public String getAccessToken(String jurisdiction) {
        log.info("Get access token for jurisdiction: {} ", jurisdiction);
        CachedIdamToken cachedIdamToken = this.accessTokenCache
            .get(jurisdiction, j -> retrieveToken(j));
        return cachedIdamToken.accessToken;
    }

    //for sonar, will remove
    public void putCachedAccessToken(String jurisdiction, CachedIdamToken cachedIdamToken ){
        this.accessTokenCache.put(jurisdiction, cachedIdamToken);
    }
    public void removeAccessTokenFromCache(String jurisdiction) {
        log.info("Remove access token from cache for jurisdiction: {} ", jurisdiction);
        accessTokenCache.invalidate(jurisdiction);
    }

    private CachedIdamToken retrieveToken(String jurisdiction) {
        log.info("Retrieve access token for jurisdiction: {} from IDAM", jurisdiction);
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

    public UserDetails getUserDetails(String accessToken) {
        log.info("Get user details");
        return this.userDetailsCache.get(accessToken, this::retrieveUserDetails);
    }

    private UserDetails retrieveUserDetails(String accessToken) {
        log.info("Retrieve user details from IDAM");
        return idamClient.getUserDetails(accessToken);
    }
}
