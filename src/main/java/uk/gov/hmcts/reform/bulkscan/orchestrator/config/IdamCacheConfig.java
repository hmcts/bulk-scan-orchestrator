package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache.AccessTokenCacheExpiry;

@Configuration
public class IdamCacheConfig {

    @Bean
    public AccessTokenCacheExpiry getAccessTokenCacheExpiry(
            @Value("${idam.client.cache.refresh-before-expire-in-sec}") long refreshTokenBeforeExpiry
    ) {
        return new AccessTokenCacheExpiry(refreshTokenBeforeExpiry);
    }
}
