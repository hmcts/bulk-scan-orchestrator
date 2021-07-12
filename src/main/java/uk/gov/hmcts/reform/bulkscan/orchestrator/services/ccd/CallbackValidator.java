package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;

@Component
public class CallbackValidator {
    @Nonnull
    public Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getCaseTypeId)
                .map(Validation::<String, String>valid)
                .orElse(invalid("Missing caseType"));
    }
}
