package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class MessageOperationsTest {

    @Mock
    private QueueClient queueClient;

    private MessageOperations messageOperations;

    @Before
    public void setUp() {
        messageOperations = new MessageOperations(queueClient);
    }

    @Test
    public void complete_should_call_queue_client() throws Exception {
        UUID lockToken = UUID.randomUUID();

        messageOperations.complete(lockToken);

        verify(queueClient).complete(lockToken);
        verifyNoMoreInteractions(queueClient);
    }

    @Test
    public void complete_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(true);
        willThrow(exceptionToThrow).given(queueClient).complete(any());

        assertThatThrownBy(() ->
            messageOperations.complete(UUID.randomUUID())
        ).isSameAs(exceptionToThrow);
    }

    @Test
    public void deadLetter_should_call_queue_client() throws Exception {
        UUID lockToken = UUID.randomUUID();
        String reason = "reason1";
        String description = "description1";

        messageOperations.deadLetter(lockToken, reason, description);

        verify(queueClient).deadLetter(eq(lockToken), eq(reason), eq(description), any());
        verifyNoMoreInteractions(queueClient);
    }

    @Test
    public void deadLetter_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(true);
        willThrow(exceptionToThrow).given(queueClient).deadLetter(any(), any(), any(), any());

        assertThatThrownBy(() ->
            messageOperations.deadLetter(UUID.randomUUID(), "reason", "description")
        ).isSameAs(exceptionToThrow);
    }
}
