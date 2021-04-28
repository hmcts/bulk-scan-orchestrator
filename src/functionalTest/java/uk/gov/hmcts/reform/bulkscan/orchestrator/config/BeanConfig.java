package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;

import static org.mockito.Mockito.mock;

@Configuration
public class BeanConfig {

    @Bean
    @Primary
    public CallbackResultRepository getCallbackResultRepository() {
        return new CallbackResultRepository(mock(NamedParameterJdbcTemplate.class));
    }
}
