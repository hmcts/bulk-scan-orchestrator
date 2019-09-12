package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ScannedDocument;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasDateField;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJourneyClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasPoBox;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateCaseEvent;

@Service
public class CreateCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseCallbackService.class);

    public CreateCaseCallbackService() {
        // currently empty constructor
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either TBD - not yet implemented
     */
    public Either<List<String>, ExceptionRecord> process(
        CaseDetails caseDetails,
        String idamToken,
        String userId,
        String eventId
    ) {
        Validation<String, Void> eventIdValidation = isCreateCaseEvent(eventId);

        if (eventIdValidation.isInvalid()) {
            String eventIdValidationError = eventIdValidation.getError();
            log.warn("Validation error {}", eventIdValidationError);

            return Either.left(singletonList(eventIdValidationError));
        }

        return getValidation(caseDetails)
            .toEither()
            .mapLeft(Seq::asJava);
    }

    private Validation<Seq<String>, ExceptionRecord> getValidation(CaseDetails caseDetails) {
        return Validation
            .combine(
                hasCaseTypeId(caseDetails),
                hasPoBox(caseDetails),
                hasJurisdiction(caseDetails),
                hasJourneyClassification(caseDetails),
                hasDateField(caseDetails, "deliveryDate"),
                hasDateField(caseDetails, "openingDate"),
                getScannedDocuments(),
                getOcrDataFields()
            )
            .ap(ExceptionRecord::new);
    }

    private Validation<String, List<ScannedDocument>> getScannedDocuments() {
        return Validation.valid(emptyList());
    }

    private Validation<String, List<OcrDataField>> getOcrDataFields() {
        return Validation.valid(emptyList());
    }
}
