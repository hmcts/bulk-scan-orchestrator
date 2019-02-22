package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.POTENTIALLY_RECOVERABLE_FAILURE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.SUCCESS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler.MessageProcessingResultType.UNRECOVERABLE_FAILURE;

public class MessageProcessingResult {

    final MessageProcessingResultType resultType;

    public final Exception exception;

    MessageProcessingResult(MessageProcessingResultType resultType) {
        this(resultType, null);
    }

    MessageProcessingResult(MessageProcessingResultType resultType, Exception exception) {
        this.resultType = resultType;
        this.exception = exception;
    }

    static MessageProcessingResult success() {
        return new MessageProcessingResult(SUCCESS);
    }

    static MessageProcessingResult recoverable(Exception exception) {
        return new MessageProcessingResult(POTENTIALLY_RECOVERABLE_FAILURE, exception);
    }

    static MessageProcessingResult unrecoverable(Exception exception) {
        return new MessageProcessingResult(UNRECOVERABLE_FAILURE, exception);
    }

    private boolean isSuccess() {
        return MessageProcessingResultType.SUCCESS.equals(resultType);
    }

    MessageProcessingResult andThen(Supplier<MessageProcessingResult> function) {
        return isSuccess() ? function.get() : this;
    }
}
