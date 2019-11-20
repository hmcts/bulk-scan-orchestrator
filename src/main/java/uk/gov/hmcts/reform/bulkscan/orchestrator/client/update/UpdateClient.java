package uk.gov.hmcts.reform.bulkscan.orchestrator.client.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.CaseClientServiceException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.update.model.request.CaseUpdate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.update.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.update.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Component
public class UpdateClient {

    private static final Logger log = LoggerFactory.getLogger(UpdateClient.class);

    private final RestTemplate restTemplate;

    private final ServiceResponseParser serviceResponseParser;

    public UpdateClient(
        RestTemplate restTemplate,
        ServiceResponseParser serviceResponseParser
    ) {
        this.restTemplate = restTemplate;
        this.serviceResponseParser = serviceResponseParser;
    }

    public SuccessfulUpdateResponse updateCase(
        String baseUrl,
        CaseDetails existingCase,
        ExceptionRecord exceptionRecord,
        String s2sToken
    ) throws CaseClientServiceException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);

        String url =
            UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/update-case")
                .build()
                .toString();

        ExistingCaseDetails existingCaseDetails = new ExistingCaseDetails(
            existingCase.getCaseTypeId(),
            existingCase.getData()
        );
        CaseUpdate caseUpdate = new CaseUpdate(exceptionRecord, existingCaseDetails);
        try {
            return restTemplate.postForObject(
                url,
                new HttpEntity<>(caseUpdate, headers),
                SuccessfulUpdateResponse.class
            );
        } catch (HttpStatusCodeException ex) {
            log.error(
                "Failed to update Case for case type {} and id {}",
                existingCase.getCaseTypeId(),
                existingCase.getId(),
                ex
            );

            if (ex.getStatusCode().equals(UNPROCESSABLE_ENTITY) || ex.getStatusCode().equals(BAD_REQUEST)) {
                serviceResponseParser.tryParseResponseBodyAndThrow(ex);
            }

            throw new CaseClientServiceException(ex, ex.getResponseBodyAsString());
        }
    }
}
