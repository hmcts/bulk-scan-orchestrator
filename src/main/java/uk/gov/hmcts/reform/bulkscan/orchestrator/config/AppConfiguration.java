package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

@Configuration
public class AppConfiguration {

    @Bean
    public BulkScanProcessorClient envelopeService(
        @Value("${bulk-scan-processor-url}") String bulkScanProcessorUrl,
        AuthTokenGenerator authTokenGenerator
    ) {
        return new BulkScanProcessorClient(
            bulkScanProcessorUrl,
            authTokenGenerator::generate
        );
    }
}
