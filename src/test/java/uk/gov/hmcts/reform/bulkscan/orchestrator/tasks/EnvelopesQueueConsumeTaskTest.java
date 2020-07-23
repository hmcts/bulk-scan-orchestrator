package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.QueueProcessingReadinessChecker;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.LogInAttemptRejectedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
public class EnvelopesQueueConsumeTaskTest {

    @Mock
    private EnvelopeMessageProcessor envelopeMessageProcessor;

    @Mock
    private QueueProcessingReadinessChecker processingReadinessChecker;

    private EnvelopesQueueConsumeTask queueConsumeTask;

    @BeforeEach
    public void setUp() throws LogInAttemptRejectedException {
        queueConsumeTask = new EnvelopesQueueConsumeTask(
            envelopeMessageProcessor,
            processingReadinessChecker
        );

        given(processingReadinessChecker.isNoLogInAttemptRejectedByIdam()).willReturn(true);
    }

    @Test
    public void consumeMessages_processes_messages_until_envelope_processor_returns_false() throws Exception {
        // given
        given(envelopeMessageProcessor.processNextMessage()).willReturn(true, true, true, false);

        // when
        queueConsumeTask.consumeMessages();

        // then
        verify(envelopeMessageProcessor, times(4)).processNextMessage();
        verify(processingReadinessChecker, times(4)).isNoLogInAttemptRejectedByIdam();
    }

    @Test
    public void consumeMessages_does_not_process_when_account_locked_in_idam() throws Exception {
        // given
        given(processingReadinessChecker.isNoLogInAttemptRejectedByIdam()).willReturn(false);

        // when
        queueConsumeTask.consumeMessages();

        // then
        verify(processingReadinessChecker, times(1)).isNoLogInAttemptRejectedByIdam();
        verify(envelopeMessageProcessor, never()).processNextMessage();
    }

    @Test
    public void consumeMessages_stops_processing_when_envelope_processor_throws_exception() throws Exception {
        // given
        willThrow(new ServiceBusException(true)).given(envelopeMessageProcessor).processNextMessage();

        // when
        queueConsumeTask.consumeMessages();

        // then
        verify(envelopeMessageProcessor, times(1)).processNextMessage();
        verify(processingReadinessChecker, times(1)).isNoLogInAttemptRejectedByIdam();
    }

    @Test
    public void consumeMessages_stops_processing_when_envelope_processor_throws_interrupted_exception()
        throws Exception {
        // given
        willThrow(new InterruptedException()).given(envelopeMessageProcessor).processNextMessage();

        // when
        queueConsumeTask.consumeMessages();

        // then
        verify(envelopeMessageProcessor, times(1)).processNextMessage();
        verify(processingReadinessChecker, times(1)).isNoLogInAttemptRejectedByIdam();
    }
}
