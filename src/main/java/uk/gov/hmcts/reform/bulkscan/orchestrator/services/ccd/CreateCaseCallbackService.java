package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.collection.Seq;
import io.vavr.control.Either;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isCreateCaseEvent;

@Service
public class CreateCaseCallbackService {

    private final CreateCaseValidator validator;

    public CreateCaseCallbackService(
        CreateCaseValidator validator
    ) {
        this.validator = validator;
    }

    /**
     * Create case record from exception case record.
     *
     * @return Either TBD - not yet implemented
     */
    public Either<List<String>, ExceptionRecord> process(CaseDetails caseDetails, String eventId) {
        return validator
            .mandatoryPrerequisites(() -> isCreateCaseEvent(eventId))
            .flatMap(aVoid -> validator
                .getValidation(caseDetails)
                .toEither()
                .mapLeft(Seq::asJava)
            );
    }
}
