package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.ProcessedEnvelope;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Notifies about successfully processed envelopes via queue.
 */
@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class ProcessedEnvelopeNotifier {

    private final Logger log = LoggerFactory.getLogger(ProcessedEnvelopeNotifier.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public ProcessedEnvelopeNotifier(
        @Qualifier("processed-envelopes") QueueClient queueClient,
        ObjectMapper objectMapper
    ) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
    }

    public void notify(String envelopeId) {
        try {
            String messageBody =
                objectMapper.writeValueAsString(new ProcessedEnvelope(envelopeId));

            IMessage message = new Message(envelopeId, messageBody, APPLICATION_JSON.toString());
            queueClient.send(message);

            log.info("Sent message to processed envelopes queue. Envelope ID: {}", envelopeId);
        } catch (Exception ex) {
            throw new MessageSendingException(
                "An error occurred when trying to send a message to processed envelopes queue",
                ex
            );
        }
    }
}
