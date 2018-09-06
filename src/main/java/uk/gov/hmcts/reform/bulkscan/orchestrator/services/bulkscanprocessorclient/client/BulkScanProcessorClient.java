package uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.exceptions.ReadEnvelopeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.model.Envelope;

import java.util.function.Supplier;

public class BulkScanProcessorClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;
    private final Supplier<String> serviceAuthTokenSupplier;

    public BulkScanProcessorClient(
        String baseUrl,
        Supplier<String> serviceAuthTokenSupplier
    ) {
        this.baseUrl = baseUrl;
        this.serviceAuthTokenSupplier = serviceAuthTokenSupplier;
    }

    public Envelope getEnvelopeById(String id) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", this.serviceAuthTokenSupplier.get());

        try {
            return restTemplate.exchange(
                this.baseUrl + "/envelopes/" + id,
                HttpMethod.GET,
                new HttpEntity<String>(headers),
                Envelope.class
            ).getBody();
        } catch (RestClientException exc) {
            throw new ReadEnvelopeException(exc);
        }
    }
}
