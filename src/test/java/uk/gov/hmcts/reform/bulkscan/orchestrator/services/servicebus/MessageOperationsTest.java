package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MessageOperationsTest {

    @Mock
    private QueueClient queueClient;

    private MessageOperations messageOperations;

    @BeforeEach
    void setUp() {
        messageOperations = new MessageOperations(queueClient);
    }

    @Test
    void complete_should_call_queue_client() throws Exception {
        UUID lockToken = UUID.randomUUID();

        messageOperations.complete(lockToken);

        verify(queueClient).complete(lockToken);
        verifyNoMoreInteractions(queueClient);
    }

    @Test
    void complete_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(true);
        willThrow(exceptionToThrow).given(queueClient).complete(any());

        assertThatThrownBy(() ->
            messageOperations.complete(UUID.randomUUID())
        ).isSameAs(exceptionToThrow);
    }

    @Test
    void deadLetter_should_call_queue_client() throws Exception {
        UUID lockToken = UUID.randomUUID();
        String reason = "reason1";
        String description = "description1";

        messageOperations.deadLetter(lockToken, reason, description);

        verify(queueClient).deadLetter(lockToken, reason, description);
        verifyNoMoreInteractions(queueClient);
    }

    @Test
    void deadLetter_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(true);
        willThrow(exceptionToThrow).given(queueClient).deadLetter(any(), any(), any());

        assertThatThrownBy(() ->
            messageOperations.deadLetter(UUID.randomUUID(), "reason", "description")
        ).isSameAs(exceptionToThrow);
    }
}
