package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ScannedDocument;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.getOcrData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasDateField;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJourneyClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasPoBox;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateCaseEvent;

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
    public Either<List<String>, ExceptionRecord> process(CaseDetails caseDetails, String eventId) {
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
                getScannedDocuments(caseDetails),
                getOcrDataFields(caseDetails)
            )
            .ap(ExceptionRecord::new);
    }

    @SuppressWarnings("unchecked")
    private Validation<String, List<ScannedDocument>> getScannedDocuments(CaseDetails caseDetails) {
        return Try.of(() ->
            Optional.ofNullable(caseDetails)
                .map(Documents::getScannedDocuments)
                .orElse(emptyList())
                .stream()
                .map(items -> items.get("value"))
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .map(document -> new ScannedDocument(
                    DocumentType.valueOf(((String) document.get("type")).toUpperCase()),
                    (String) document.get("subType"),
                    ((Map<String, String>) document.get("url")).get("document_url"),
                    (String) document.get("controlNumber"),
                    (String) document.get("fileName"),
                    Instant.parse((String) document.get("scannedDate")),
                    Instant.parse((String) document.get("deliveryDate"))
                ))
                .collect(toList())
        ).toValidation().mapError(throwable -> "Invalid scannedDocuments format. Error: " + throwable.getMessage());
    }

    @SuppressWarnings("unchecked")
    private Validation<String, List<OcrDataField>> getOcrDataFields(CaseDetails caseDetails) {
        return getOcrData(caseDetails)
            // following mapError should never happen as getting should be non-breaking
            // left side must be String
            .mapError(Object::toString)
            .flatMap(fields ->
                Try.of(() -> fields
                    .stream()
                    .map(items -> items.get("value"))
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, String>) item)
                    .map(ocrData -> new OcrDataField(
                        ocrData.get("key"),
                        ocrData.get("value")
                    ))
                    .collect(toList())
                ).toValidation().mapError(throwable -> "Invalid OCR data format. Error: " + throwable.getMessage())
            );
    }
}
