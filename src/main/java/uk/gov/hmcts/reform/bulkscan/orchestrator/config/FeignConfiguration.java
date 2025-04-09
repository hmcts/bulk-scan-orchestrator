package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfiguration {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000, 3000, 3);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new RetryableErrorDecoder();
    }
}
