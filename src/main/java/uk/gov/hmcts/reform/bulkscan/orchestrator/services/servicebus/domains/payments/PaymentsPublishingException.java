package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@SuppressWarnings("serial")
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class PaymentsPublishingException extends RuntimeException {

    public PaymentsPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
