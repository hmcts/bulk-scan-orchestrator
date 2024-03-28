package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import feign.Logger;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpConfiguration {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(clientHttpRequestFactory());
    }

    @Bean
    public HttpComponentsClientHttpRequestFactory clientHttpRequestFactory() {
        return new HttpComponentsClientHttpRequestFactory(getHttp5Client());
    }

    private org.apache.hc.client5.http.classic.HttpClient getHttp5Client() {
        org.apache.hc.client5.http.config.RequestConfig config =
            org.apache.hc.client5.http.config.RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                .build();

        return org.apache.hc.client5.http.impl.classic.HttpClientBuilder
            .create()
            .useSystemProperties()
            .setDefaultRequestConfig(config)
            .build();
    }

}
