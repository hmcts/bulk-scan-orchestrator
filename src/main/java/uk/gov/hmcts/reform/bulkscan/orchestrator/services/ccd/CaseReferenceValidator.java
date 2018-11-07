package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Option;
import io.vavr.control.Validation;
import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static io.vavr.control.Validation.invalid;
import static java.lang.String.format;

class CaseReferenceValidator {
    private static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

    Validation<String, String> validate(CaseDetails theCase) {
        return Option.of(theCase)
            .flatMap(c -> Option.of(c.getData()))
            .flatMap(data -> Option.of(data.get(ATTACH_TO_CASE_REFERENCE)))
            .flatMap(this::validateAndConvert)
            .getOrElse(() -> invalid("No case reference supplied"));
    }

    private Option<Validation<String, String>> validateAndConvert(Object item) {
        return Option.of(item)
            .filter(ref -> ref instanceof String)
            .map(ref -> ((String) ref))
            .map(ref -> ref.replaceAll("[^0-9]", ""))
            .filter(StringUtils::isNotEmpty)
            .map(Validation::<String, String>valid)
            .orElse(() -> Option.of(invalid(format("Invalid case reference: '%s'", item))));
    }
}
