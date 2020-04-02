package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ValidatableRestTemplate;

@Component
public class TransformationClient {

    private final ValidatableRestTemplate restTemplate;

    public TransformationClient(
        ValidatableRestTemplate restTemplate
    ) {
        this.restTemplate = restTemplate;
    }

    public SuccessfulTransformationResponse transformExceptionRecord(
        String baseUrl,
        ExceptionRecord exceptionRecord,
        String s2sToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);

        String url =
            UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/transform-exception-record")
                .build()
                .toString();

        return restTemplate.post(
            url,
            new HttpEntity<>(exceptionRecord, headers),
            SuccessfulTransformationResponse.class
        );
    }
}
