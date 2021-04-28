package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class BeanConfig {

    @Bean("callbackResultRepository")
    @Primary
    public CallbackResultRepository getCallbackResultRepository() {
        return new CallbackResultRepository(mock(NamedParameterJdbcTemplate.class));
    }
}
