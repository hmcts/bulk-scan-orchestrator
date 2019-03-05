package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

public class UnknownEventException extends RuntimeException {

    UnknownEventException(String message) {
        super(message);
    }
}
