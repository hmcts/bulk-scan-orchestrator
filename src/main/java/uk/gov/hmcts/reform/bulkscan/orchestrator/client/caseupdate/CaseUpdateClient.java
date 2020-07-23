package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.CaseUpdate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request.ExistingCaseDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

@Component
public class CaseUpdateClient {

    private static final Logger log = LoggerFactory.getLogger(CaseUpdateClient.class);

    private final Validator validator;
    private final RestTemplate restTemplate;

    public CaseUpdateClient(
        Validator validator,
        RestTemplate restTemplate
    ) {
        this.validator = validator;
        this.restTemplate = restTemplate;
    }

    public SuccessfulUpdateResponse updateCase(
        String baseUrl,
        CaseDetails existingCase,
        ExceptionRecord exceptionRecord,
        String s2sToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", s2sToken);

        String url =
            UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/update-case")
                .build()
                .toString();

        ExistingCaseDetails existingCaseDetails = new ExistingCaseDetails(
            existingCase.getId().toString(),
            existingCase.getCaseTypeId(),
            existingCase.getData()
        );

        CaseUpdate caseUpdate = new CaseUpdate(exceptionRecord, false, null, existingCaseDetails);

        log.info(
            "Requesting service to update case, caseTypeId: {}, case id: {}, exception id: {}",
            caseUpdate.caseDetails.caseTypeId,
            caseUpdate.caseDetails.id,
            caseUpdate.exceptionRecord.id
        );

        SuccessfulUpdateResponse response = restTemplate.postForObject(
            url,
            new HttpEntity<>(caseUpdate, headers),
            SuccessfulUpdateResponse.class
        );

        Set<ConstraintViolation<SuccessfulUpdateResponse>> violations = validator.validate(response);

        if (violations.isEmpty()) {
            return response;
        } else {
            throw new ConstraintViolationException(violations);
        }
    }
}
