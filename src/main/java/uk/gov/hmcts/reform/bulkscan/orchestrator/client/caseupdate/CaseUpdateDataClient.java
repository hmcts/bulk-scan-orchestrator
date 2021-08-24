package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdateRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Retrieves data that should be used to update a case.
 */
@Component
public class CaseUpdateDataClient {

    private static final Logger log = LoggerFactory.getLogger(CaseUpdateDataClient.class);

    private final Validator validator;
    private final RestTemplate restTemplate;

    public CaseUpdateDataClient(
        Validator validator,
        RestTemplate restTemplate
    ) {
        this.validator = validator;
        this.restTemplate = restTemplate;
    }

    public SuccessfulUpdateResponse getCaseUpdateData(
        String updateUrl,
        String s2sToken,
        CaseUpdateRequest caseUpdateRequest
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);
        headers.add("Content-Type", APPLICATION_JSON.toString());

        String url =
            UriComponentsBuilder
                .fromHttpUrl(updateUrl)
                .build()
                .toString();

        log.info("Requesting service to update case. {}", requestInfo(caseUpdateRequest));

        SuccessfulUpdateResponse response = restTemplate.postForObject(
            url,
            new HttpEntity<>(caseUpdateRequest, headers),
            SuccessfulUpdateResponse.class
        );

        Set<ConstraintViolation<SuccessfulUpdateResponse>> violations = validator.validate(response);

        if (violations.isEmpty()) {
            return response;
        } else {
            throw new ConstraintViolationException(violations);
        }
    }

    private String requestInfo(CaseUpdateRequest req) {
        return String.format(
            "caseTypeId: %s, case id: %s, exception id: %s",
            req.caseDetails.caseTypeId,
            req.caseDetails.id,
            req.exceptionRecord != null ? req.exceptionRecord.id : "null"
        );
    }
}
