package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGeneratorFactory;
import uk.gov.hmcts.reform.idam.client.IdamApi;

@Configuration
@EnableFeignClients(clients = IdamApi.class)
public class AuthConfig {

    @Bean("processor-s2s-auth")
    public AuthTokenGenerator authTokenGenerator(
        @Value("${idam.s2s-auth.processor.secret}") String secret,
        @Value("${idam.s2s-auth.processor.name}") String name,
        ServiceAuthorisationApi serviceAuthorisationApi
    ) {
        return AuthTokenGeneratorFactory.createDefaultGenerator(secret, name, serviceAuthorisationApi);
    }
}
