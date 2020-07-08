package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.CCD_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE_TYPE;

class CaseReferenceValidator {

    private static final List<String> VALID_CASE_REFERENCE_TYPES = Arrays.asList(
        CCD_CASE_REFERENCE,
        EXTERNAL_CASE_REFERENCE
    );

    @Nonnull
    Validation<String, String> validateTargetCaseReference(CaseDetails theCase) {
        return getCaseRef(theCase, SEARCH_CASE_REFERENCE)
            .map(this::validateCcdCaseRef)
            .orElseGet(() -> invalid("No case reference supplied"));
    }

    @Nonnull
    Validation<String, String> validateSearchCaseReferenceWithSearchType(CaseDetails theCase) {
        Validation<String, String> caseReferenceTypeValidation = validateCaseReferenceType(theCase);

        if (caseReferenceTypeValidation.isValid()) {
            Function<Object, Validation<String, String>> validationFunction =
                CCD_CASE_REFERENCE.equals(caseReferenceTypeValidation.get())
                    ? this::validateCcdCaseRef
                    : this::validateExternalCaseRef;

            return getCaseRef(theCase, SEARCH_CASE_REFERENCE)
                .map(validationFunction)
                .orElseGet(() -> invalid("No case reference supplied"));
        } else {
            return invalid("Cannot validate case reference due to the lack of valid case reference type");
        }
    }

    @Nonnull
    Validation<String, String> validateCaseReferenceType(CaseDetails theCase) {
        return getSearchCaseReferenceType(theCase)
            .map(searchCaseReferenceType -> {
                    if (VALID_CASE_REFERENCE_TYPES.contains(searchCaseReferenceType)) {
                        return Validation.<String, String>valid((String) searchCaseReferenceType);
                    } else {
                        return Validation.<String, String>invalid(
                            "Invalid case reference type supplied: " + searchCaseReferenceType
                        );
                    }
                }
            ).orElseGet(() -> invalid("No case reference type supplied"));
    }

    private Validation<String, String> validateCcdCaseRef(Object caseRef) {
        return Optional.of(caseRef)
            .filter(ref -> ref instanceof String)
            .map(ref -> ((String) ref).replaceAll("[^0-9]", ""))
            .filter(ref -> !Strings.isNullOrEmpty(ref))
            .map(Validation::<String, String>valid)
            .orElseGet(() -> invalid(format("Invalid case reference: '%s'", caseRef)));
    }

    private Validation<String, String> validateExternalCaseRef(Object caseRef) {
        return Optional.of(caseRef)
            .filter(ref -> ref instanceof String)
            .map(ref -> ((String) ref).trim())
            .filter(ref -> !Strings.isNullOrEmpty(ref))
            .map(Validation::<String, String>valid)
            .orElseGet(() -> invalid(format("Invalid external case reference: '%s'", caseRef)));
    }

    private Optional<Object> getCaseRef(CaseDetails theCase, String caseRefFieldName) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(caseRefFieldName));
    }

    private Optional<Object> getSearchCaseReferenceType(CaseDetails theCase) {
        return Optional
            .ofNullable(theCase)
            .map(CaseDetails::getData)
            .filter(data -> data != null)
            .map(data -> data.get(SEARCH_CASE_REFERENCE_TYPE))
            .filter(refType -> refType != null);
    }
}
