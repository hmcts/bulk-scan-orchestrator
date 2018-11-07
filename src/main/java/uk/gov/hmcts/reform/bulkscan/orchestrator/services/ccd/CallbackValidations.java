package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

final class CallbackValidations {
    private static final Logger log = LoggerFactory.getLogger(CallbackValidations.class);
    private static final String ATTACH_TO_EXISTING_CASE = "attachToExistingCase";

    private static final CaseReferenceValidator caseRefValidator = new CaseReferenceValidator();
    private static final ScannedRecordValidator scannedRecordValidator = new ScannedRecordValidator();

    private CallbackValidations() {
    }

    @NotNull
    static Validation<String, CaseDetails> hasCaseDetails(CaseDetails caseDetails) {
        return caseDetails != null
            ? valid(caseDetails)
            : internalError("no case details supplied", null);
    }

    @NotNull
    static Validation<String, String> isAttachToCaseEvent(String eventId) {
        return ATTACH_TO_EXISTING_CASE.equals(eventId)
            ? valid(eventId)
            : internalError("event-id: %s invalid", eventId);
    }

    @NotNull
    private static <T> Validation<String, T> internalError(String error, T arg1) {
        log.error("{}:{}", error, arg1);
        String formatString = "Internal Error: " + error;
        return invalid(format(formatString, arg1));
    }

    @NotNull
    static Validation<String, String> hasJurisdiction(CaseDetails theCase) {
        String jurisdiction = null;
        return theCase != null
            && (jurisdiction = theCase.getJurisdiction()) != null
            ? valid(jurisdiction)
            : internalError("invalid jurisdiction supplied: %s", jurisdiction);
    }

    @NotNull
    static Validation<String, String> hasCaseReference(CaseDetails theCase) {
        return caseRefValidator.validate(theCase);
    }

    private static Optional<String> validRef(Object reference) {
        return Optional.of(reference)
            .filter(ref -> ref instanceof String)
            .map(ref -> ((String) ref).replaceAll("[^0-9]", ""))
            .filter(ref -> !ref.isEmpty());
    }

    @NotNull
    static Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        String caseTypeId = null;
        return theCase != null
            && (caseTypeId = theCase.getCaseTypeId()) != null
            && !isNullOrEmpty(caseTypeId)
            ? valid(caseTypeId)
            : internalError("No caseType supplied: %s", caseTypeId);
    }

    @NotNull
    static Validation<String, String> isAttachEvent(String type) {
        return "attach_case".equals(type)
            ? valid(type)
            : internalError("invalid type supplied: %s", type);
    }

    @NotNull
    static Validation<String, List<Map<String, Object>>> hasAScannedDocument(CaseDetails theCase) {
        return scannedRecordValidator.validate(theCase);

    }
}
