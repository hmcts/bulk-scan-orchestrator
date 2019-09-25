package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.getOcrData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasDateField;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJourneyClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasPoBox;

@Component
public class CreateCaseValidator {

    private static final Logger log = LoggerFactory.getLogger(CreateCaseValidator.class);

    public CreateCaseValidator() {
        // empty constructor
    }

    /**
     * Any prerequisites to execute prior further action. Failing fast.
     * Easy extension for more mandatory prerequisites - just flatmap next Validation.
     * @param prerequisites Top level requirements failing fast
     * @return Either singleton list of errors or green pass to proceed further
     */
    @SafeVarargs
    public final Either<List<String>, Void> mandatoryPrerequisites(
        Supplier<Validation<String, Void>>... prerequisites
    ) {
        for (Supplier<Validation<String, Void>> prerequisite : prerequisites) {
            Validation<List<String>, Void> requirement = prerequisite.get()
                .mapError(error -> {
                    log.warn("Validation error {}", error);

                    return singletonList(error);
                });

            if (requirement.isInvalid()) {
                return requirement.toEither();
            }
        }

        return Either.right(null);
    }

    public Validation<Seq<String>, ExceptionRecord> getValidation(CaseDetails caseDetails) {
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
                .map(this::mapScannedDocument)
                .collect(toList())
        ).toValidation().mapError(throwable -> "Invalid scannedDocuments format. Error: " + throwable.getMessage());
    }

    @SuppressWarnings("unchecked")
    private Validation<String, List<OcrDataField>> getOcrDataFields(CaseDetails caseDetails) {
        return getOcrData(caseDetails)
            .map(ocrDataList ->
                Try.of(() -> ocrDataList
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
            )
            .orElse(Validation.valid(emptyList()));
    }

    @SuppressWarnings("unchecked")
    private ScannedDocument mapScannedDocument(Map<String, Object> document) {
        return new ScannedDocument(
            DocumentType.valueOf(((String) document.get("type")).toUpperCase()),
            (String) document.get("subType"),
            ((Map<String, String>) document.get("url")).get("document_url"),
            (String) document.get("controlNumber"),
            (String) document.get("fileName"),
            Instant.parse((String) document.get("scannedDate")),
            Instant.parse((String) document.get("deliveryDate"))
        );
    }
}
