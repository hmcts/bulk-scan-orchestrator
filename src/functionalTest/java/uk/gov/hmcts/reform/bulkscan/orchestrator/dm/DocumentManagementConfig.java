package uk.gov.hmcts.reform.bulkscan.orchestrator.dm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DocumentManagementConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
