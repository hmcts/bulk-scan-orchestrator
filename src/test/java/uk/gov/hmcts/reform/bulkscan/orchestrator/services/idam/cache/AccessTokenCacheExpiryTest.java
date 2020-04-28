package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenCacheExpiryTest {

    private AccessTokenCacheExpiry accessTokenCacheExpiry = new AccessTokenCacheExpiry(20);


    @ParameterizedTest
    @CsvSource({
        "20, 1212, 0",
        "2, 9812233, -18000000000",
        "22, 0, 2000000000"
    })
    void expireAfterCreate(long expireIn, long currentTime, long result) {
        CachedIdamToken cachedIdamToken = new CachedIdamToken("token", expireIn);
        long remainingTime = accessTokenCacheExpiry.expireAfterCreate(
            "key_9090",
            cachedIdamToken,
            currentTime
        );
        assertThat(remainingTime).isEqualTo(result);
    }


    @ParameterizedTest
    @CsvSource({
        "20, 1212, 0, 0",
        "2, 9812233, 1200, 1200",
        "22, 0, 420, 420"
    })
    void expireAfterUpdate(long expireIn, long currentTime, long currentDuration, long result) {
        CachedIdamToken cachedIdamToken = new CachedIdamToken("token", expireIn);

        long remainingTime = accessTokenCacheExpiry.expireAfterUpdate(
            "key_32x",
            cachedIdamToken,
            currentTime,
            currentDuration
        );

        assertThat(remainingTime).isEqualTo(result);
    }

    @ParameterizedTest
    @CsvSource({
        "20, 122212, 0, 0",
        "2, 9812233, 1200, 1200",
        "22, 0, 120, 120"
    })
    void expireAfterRead(long expireIn, long currentTime, long currentDuration, long result) {
        CachedIdamToken cachedIdamToken = new CachedIdamToken("token", expireIn);

        long remainingTime = accessTokenCacheExpiry.expireAfterRead(
            "21321",
            cachedIdamToken,
            currentTime,
            currentDuration
        );
        assertThat(remainingTime).isEqualTo(result);
    }

}