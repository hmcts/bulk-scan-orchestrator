package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler;

public class MessageProcessingResult {

    public final MessageProcessingResultType resultType;

    public final Exception exception;

    public MessageProcessingResult(MessageProcessingResultType resultType) {
        this(resultType, null);
    }

    public MessageProcessingResult(MessageProcessingResultType resultType, Exception exception) {
        this.resultType = resultType;
        this.exception = exception;
    }
}
