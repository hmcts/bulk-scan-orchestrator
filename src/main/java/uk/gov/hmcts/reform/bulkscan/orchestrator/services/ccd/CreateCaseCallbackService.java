package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Either;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static java.util.Collections.singletonList;
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
    public Either<List<String>, Object> process(CaseDetails caseDetails, String eventId) {
        Validation<String, Void> eventIdValidation = isCreateCaseEvent(eventId);

        if (eventIdValidation.isInvalid()) {
            String eventIdValidationError = eventIdValidation.getError();
            log.warn("Validation error {}", eventIdValidationError);

            return Either.left(singletonList(eventIdValidationError));
        }

        return Either.left(singletonList("Not yet implemented"));
    }
}
