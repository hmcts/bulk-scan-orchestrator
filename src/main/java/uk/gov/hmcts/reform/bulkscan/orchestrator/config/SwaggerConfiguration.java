package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("bulkscan-orchestrator-public")
            .pathsToMatch("/controllers/**")
            .build();
    }
}
