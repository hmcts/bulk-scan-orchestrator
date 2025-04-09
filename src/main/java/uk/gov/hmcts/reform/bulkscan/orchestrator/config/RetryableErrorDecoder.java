package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

public class RetryableErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() >= 300) {
            return new RetryableException(
                response.status(),
                "Retrying due to status: " + response.status(),
                response.request().httpMethod(),
                Long.getLong("100"),
                response.request()
            );
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
