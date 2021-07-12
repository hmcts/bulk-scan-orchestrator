package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;

@Component
public class CallbackValidator {
    private static final Logger log = LoggerFactory.getLogger(CallbackValidator.class);

    @Nonnull
    public Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getCaseTypeId)
                .map(Validation::<String, String>valid)
                .orElse(invalid("Missing caseType"));
    }

    @Nonnull
    public Validation<String, String> hasFormType(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> (String) data.get("formType"))
                .map(Validation::<String, String>valid)
                .orElse(invalid("Missing Form Type"));
    }
}
