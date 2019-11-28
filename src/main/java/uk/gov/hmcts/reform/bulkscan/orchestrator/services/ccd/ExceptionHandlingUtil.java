package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.bulkscan.orchestrator.client.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class ExceptionHandlingUtil {
    private ExceptionHandlingUtil() {
        //
    }

    static CallbackException handleGenericException(Exception exception, String msg) {
        // log happens individually to cover update/ccd cases
        return new CallbackException(msg, exception);
    }

    static ProcessResult handleInvalidCaseDataException(InvalidCaseDataException exception, String msg) {
        if (BAD_REQUEST.equals(exception.getStatus())) {
            throw handleGenericException(exception, msg);
        } else {
            return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
        }
    }
}
