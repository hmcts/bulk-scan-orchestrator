package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class MultipleCasesFoundException extends RuntimeException {

    public MultipleCasesFoundException(String message) {
        super(message);
    }
}
