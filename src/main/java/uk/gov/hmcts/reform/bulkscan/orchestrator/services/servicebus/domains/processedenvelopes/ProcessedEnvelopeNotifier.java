package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Notifies about successfully processed envelopes via queue.
 */
@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class ProcessedEnvelopeNotifier implements IProcessedEnvelopeNotifier {

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

    public void notify(String envelopeId, Long ccdId, EnvelopeCcdAction envelopeCcdAction) {
        try {
            String messageBody =
                objectMapper.writeValueAsString(new ProcessedEnvelope(envelopeId, ccdId, envelopeCcdAction));

            IMessage message = new Message(envelopeId, messageBody, APPLICATION_JSON.toString());

            // TODO: change back to `queueClient.send(message)` when BPS-694 is implemented
            queueClient.scheduleMessage(message, Instant.now().plusSeconds(10));

            log.info("Sent message to processed envelopes queue. Message Body: {}", messageBody);
        } catch (Exception ex) {
            throw new NotificationSendingException(
                "An error occurred when trying to send notification about successfully processed envelope",
                ex
            );
        }
    }
}
