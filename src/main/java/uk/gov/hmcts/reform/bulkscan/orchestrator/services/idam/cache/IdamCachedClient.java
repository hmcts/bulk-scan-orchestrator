package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;

@Service
public class IdamCachedClient {

    public static final String BEARER_AUTH_TYPE = "Bearer ";
    public static final String EXPIRES_IN = "expires_in";

    private static Cache<String, CachedIdamToken> accessTokenCache;

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
            .build();
    }

    public String getAccessToken(String jurisdiction) {
        CachedIdamToken cachedIdamToken = this.accessTokenCache
            .get(jurisdiction, j -> retrieveToken(j));
        return cachedIdamToken.accessToken;
    }

    public void removeAccessTokenFromCache(String jurisdiction) {
        this.accessTokenCache.invalidate(jurisdiction);
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

}
