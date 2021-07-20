package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import io.vavr.collection.Array;
import io.vavr.collection.Seq;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.FORMATTER;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.getOcrData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasDateField;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJourneyClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasPoBox;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@Component
public class ExceptionRecordValidator {

    private static final String VALUE = "value";
    private static final String KEY = "key";

    private final CallbackValidator callbackValidator;

    public ExceptionRecordValidator(CallbackValidator callbackValidator) {
        this.callbackValidator = callbackValidator;
    }

    /**
     * Any prerequisites to execute prior further action. Failing fast.
     * Easy extension for more mandatory prerequisites - just flatmap next Validation.
     *
     * @param prerequisites Top level requirements failing fast
     * @return Validation an error or green pass to proceed further
     */
    @SafeVarargs
    public final Validation<String, Void> mandatoryPrerequisites(
        Supplier<Validation<String, Void>>... prerequisites
    ) {
        for (Supplier<Validation<String, Void>> prerequisite : prerequisites) {
            Validation<String, Void> requirement = prerequisite.get();

            if (requirement.isInvalid()) {
                return requirement;
            }
        }

        return Validation.valid(null);
    }

    public Validation<String, String> getCaseId(CaseDetails caseDetails) {
        return callbackValidator.hasAnId(caseDetails).map(id -> Long.toString(id));
    }

    public Validation<Seq<String>, ExceptionRecord> getValidation(CaseDetails caseDetails) {
        Validation<String, String> exceptionRecordIdValidation = getCaseId(caseDetails);
        Validation<String, String> caseTypeIdValidation = callbackValidator.hasCaseTypeId(caseDetails);
        Validation<String, String> poBoxValidation = hasPoBox(caseDetails);
        Validation<String, String> jurisdictionValidation = callbackValidator.hasJurisdiction(caseDetails);
        Validation<String, Classification> journeyClassificationValidation = hasJourneyClassification(caseDetails);

        Validation<String, String> formTypeValidation = callbackValidator.hasFormType(caseDetails);
        // Exception journey classification may not have form type so skipping validation for it
        if (journeyClassificationValidation.isValid() && journeyClassificationValidation.get().equals(EXCEPTION)) {
            formTypeValidation = Validation.valid(formTypeValidation.getOrNull());
        }

        Validation<String, LocalDateTime> deliveryDateValidation = hasDateField(caseDetails, "deliveryDate");
        Validation<String, LocalDateTime> openingDateValidation = hasDateField(caseDetails, "openingDate");
        Validation<String, List<ScannedDocument>> scannedDocumentsValidation = getScannedDocuments(caseDetails);
        Validation<String, List<OcrDataField>> ocrDataFieldsValidation = getOcrDataFields(caseDetails);

        Seq<Validation<String, ?>> validations = Array.of(
            exceptionRecordIdValidation,
            caseTypeIdValidation,
            poBoxValidation,
            jurisdictionValidation,
            formTypeValidation,
            journeyClassificationValidation,
            deliveryDateValidation,
            openingDateValidation,
            scannedDocumentsValidation,
            ocrDataFieldsValidation
        );

        Seq<String> errors = getValidationErrors(validations);
        if (errors.isEmpty()) {
            return Validation.valid(
                new ExceptionRecord(
                    exceptionRecordIdValidation.get(),
                    caseTypeIdValidation.get(),
                    getEnvelopeId(caseDetails),
                    poBoxValidation.get(),
                    jurisdictionValidation.get(),
                    journeyClassificationValidation.get(),
                    formTypeValidation.get(),
                    deliveryDateValidation.get(),
                    openingDateValidation.get(),
                    scannedDocumentsValidation.get(),
                    ocrDataFieldsValidation.get()
                )
            );
        }
        return Validation.invalid(errors);
    }

    public Validation<String, String> hasServiceNameInCaseTypeId(CaseDetails theCase) {
        return callbackValidator.hasServiceNameInCaseTypeId(theCase);
    }

    public Validation<String, String> hasIdamToken(String idamToken) {
        return callbackValidator.hasIdamToken(idamToken);
    }

    public Validation<String, String> hasUserId(String userId) {
        return callbackValidator.hasUserId(userId);
    }

    private Seq<String> getValidationErrors(Seq<Validation<String, ?>> validations) {
        return validations
            .filter(Validation::isInvalid)
            .map(Validation::getError);
    }

    @SuppressWarnings("unchecked")
    private Validation<String, List<ScannedDocument>> getScannedDocuments(CaseDetails caseDetails) {
        return Try.of(() ->
            Optional.ofNullable(caseDetails)
                .map(Documents::getScannedDocuments)
                .orElse(emptyList())
                .stream()
                .map(items -> items.get(VALUE))
                .filter(item -> item instanceof Map)
                .map(item -> (Map<String, Object>) item)
                .map(this::mapScannedDocument)
                .collect(toList())
        ).toValidation().mapError(throwable -> "Invalid scannedDocuments format. Error: " + throwable.getMessage());
    }

    private String getEnvelopeId(CaseDetails caseDetails) {
        return Optional.ofNullable(caseDetails)
            .map(CaseDetails::getData)
            .map(data -> (String) data.get(ENVELOPE_ID))
            .orElseThrow(
                // we can't apply standard validation, as the caseworker doesn't even know about envelope ID field
                () -> new UnprocessableCaseDataException(
                    format("Exception record is lacking %s field", ENVELOPE_ID)
                )
            );
    }

    @SuppressWarnings("unchecked")
    private Validation<String, List<OcrDataField>> getOcrDataFields(CaseDetails caseDetails) {
        return getOcrData(caseDetails)
            .map(ocrDataList ->
                Try.of(() -> ocrDataList
                    .stream()
                    .map(items -> items.get(VALUE))
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, String>) item)
                    .map(ocrData -> new OcrDataField(
                        ocrData.get(KEY),
                        ocrData.get(VALUE)
                    ))
                    .collect(toList())
                ).toValidation().mapError(throwable -> "Invalid OCR data format. Error: " + throwable.getMessage())
            )
            .orElse(Validation.valid(emptyList()));
    }

    @SuppressWarnings("unchecked")
    private ScannedDocument mapScannedDocument(Map<String, Object> document) {
        Map<String, String> ccdUrl = (Map<String, String>) document.get("url");
        return new ScannedDocument(
            DocumentType.valueOf(((String) document.get("type")).toUpperCase()),
            (String) document.get("subtype"),
            new DocumentUrl(
                ccdUrl.get("document_url"),
                ccdUrl.get("document_binary_url"),
                ccdUrl.get("document_filename")
            ),
            (String) document.get("controlNumber"),
            (String) document.get("fileName"),
            LocalDateTime.parse((String) document.get("scannedDate"), FORMATTER),
            LocalDateTime.parse((String) document.get("deliveryDate"), FORMATTER)
        );
    }
}
