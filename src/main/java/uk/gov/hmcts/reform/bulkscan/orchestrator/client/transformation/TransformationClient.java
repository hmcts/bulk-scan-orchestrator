package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;

@Component
public class TransformationClient {

    private static final Logger log = LoggerFactory.getLogger(TransformationClient.class);

    private final RestTemplate restTemplate;

    public TransformationClient(
        RestTemplate restTemplate
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

        try {
            return restTemplate.postForObject(
                url,
                new HttpEntity<>(exceptionRecord, headers),
                SuccessfulTransformationResponse.class
            );
        } catch (HttpStatusCodeException ex) {
            log.error(
                "Failed to transform Exception Record to case data for case type {} and id {}",
                exceptionRecord.caseTypeId,
                exceptionRecord.id,
                ex
            );

            throw ex;
        }
    }
}
