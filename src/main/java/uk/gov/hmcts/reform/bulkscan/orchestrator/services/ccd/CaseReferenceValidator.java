package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static java.lang.String.format;

class CaseReferenceValidator {
    private static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

    @Nonnull
    Validation<String, String> validate(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(ATTACH_TO_CASE_REFERENCE))
            .flatMap(this::validateCaseRef)
            .orElseGet(() -> invalid("No case reference supplied"));
    }

    private Optional<Validation<String, String>> validateCaseRef(Object caseRef) {
        Optional<Validation<String, String>> valid = Optional.of(caseRef)
            .filter(aRef -> aRef instanceof String)
            .map(aRef -> ((String) aRef).replaceAll("[^0-9]", ""))
            .map(Strings::emptyToNull)
            .map(Validation::valid);
        //This is done because java 8 does not have "Optional<T> orElse( Optional<T>)"
        if (valid.isPresent()) {
            return valid;
        } else {
            return Optional.of(invalid(format("Invalid case reference: '%s'", caseRef)));
        }
    }
}
