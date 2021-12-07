package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class CallbackService {
    protected final ServiceConfigProvider serviceConfigProvider;
    protected final ExceptionRecordValidator exceptionRecordValidator;

    protected CallbackService(
            ServiceConfigProvider serviceConfigProvider,
            ExceptionRecordValidator exceptionRecordValidator
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.exceptionRecordValidator = exceptionRecordValidator;
    }

    @SuppressWarnings("unchecked")
    protected final Validation<String, Void> canAccess(
            String idamToken,
            String userId,
            List<Supplier<Validation<String, Void>>> prerequisites
    ) {
        List<Supplier<Validation<String, Void>>> allPrerequisites = new ArrayList<>(prerequisites);
        allPrerequisites.add(() -> exceptionRecordValidator.hasIdamToken(idamToken).map(item -> null));
        allPrerequisites.add(() -> exceptionRecordValidator.hasUserId(userId).map(item -> null));
        Supplier[] allPrerequisitesArray = new Supplier[allPrerequisites.size()];
        return exceptionRecordValidator.mandatoryPrerequisites(allPrerequisites.toArray(allPrerequisitesArray));
    }
}
