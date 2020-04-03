package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@Service
public class ReclassifyCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ReclassifyCallbackService.class);

    private static final String REQUIRED_INITIAL_STATE = "ScannedRecordReceived";
    private static final String REQUIRED_INITIAL_CLASSIFICATION = NEW_APPLICATION.name();

    public ProcessResult reclassifyExceptionRecord(CaseDetails exceptionRecordDetails, String userId) {
        Long exceptionRecordId = exceptionRecordDetails.getId();
        String jurisdiction = exceptionRecordDetails.getJurisdiction();

        log.info(
            "Reclassifying exception record. Exception record ID: {}. Jurisdiction: {}. User ID: {}",
            exceptionRecordId,
            jurisdiction,
            userId
        );

        Optional<String> validationError = validateClassification(exceptionRecordDetails);

        if (!validationError.isPresent()) {
            log.info(
                "Returning successful reclassification result for exception record. "
                    + "Exception record ID: {}. Jurisdiction: {}. User ID: {}",
                exceptionRecordId,
                jurisdiction,
                userId
            );

            return new ProcessResult(updateClassification(exceptionRecordDetails.getData()));
        } else {
            // Logging validation failure as error, because the caseworker should have no way of causing it.
            // If it happens, the team needs to be alerted.
            log.error(
                "Validation failed for exception record reclassification. "
                + "Exception record ID: {}. Jurisdiction: {}. User ID: {}. Error: {}",
                exceptionRecordId,
                jurisdiction,
                userId,
                validationError.get()
            );

            return new ProcessResult(emptyList(), asList(validationError.get()));
        }
    }

    private Map<String, Object> updateClassification(Map<String, Object> exceptionRecordData) {
        Map<String, Object> updatedFields = Maps.newHashMap(exceptionRecordData);
        updatedFields.put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name());
        return updatedFields;
    }

    private Optional<String> validateClassification(CaseDetails exceptionRecordDetails) {
        String classification = exceptionRecordDetails.getData().get(JOURNEY_CLASSIFICATION).toString();
        if (!Objects.equals(classification, REQUIRED_INITIAL_CLASSIFICATION)) {
            return Optional.of(
                String.format(
                    "This exception record's journey classification is '%s'. "
                        + "Reclassification is only possible from '%s' classification.",
                    classification,
                    REQUIRED_INITIAL_CLASSIFICATION
                )
            );
        } else {
            return Optional.empty();
        }
    }
}
