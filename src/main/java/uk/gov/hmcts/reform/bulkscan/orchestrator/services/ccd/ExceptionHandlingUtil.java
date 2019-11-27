package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import uk.gov.hmcts.reform.bulkscan.orchestrator.client.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

public class ExceptionHandlingUtil {
    private ExceptionHandlingUtil() {
        //
    }

    public static ProcessResult handleGenericException(Exception exception, String msg) {
        // log happens individually to cover update/ccd cases
        throw new CallbackException(msg, exception);
    }

    public static ProcessResult handleInvalidCaseDataException(InvalidCaseDataException exception, String msg) {
        if (BAD_REQUEST.equals(exception.getStatus())) {
            return handleGenericException(exception, msg);
        } else {
            return new ProcessResult(exception.getResponse().warnings, exception.getResponse().errors);
        }
    }
}
