package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI api() {
        return new OpenAPI()
            .info(new Info().title("Bulk scan Orchestrator API")
                .description("Callback handlers")
                .version("v0.0.1"));
    }
}
