package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotificationSendingException;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Notifies about successfully processed envelopes via queue.
 */
@Service
@Profile("!nosb") // do not register for the nosb (test) profile
@ConditionalOnExpression("!${jms.enabled}")
public class ProcessedEnvelopeNotifier implements IProcessedEnvelopeNotifier {

    // TODO: make jms version of this

    private final Logger log = LoggerFactory.getLogger(ProcessedEnvelopeNotifier.class);

    private final ServiceBusSenderClient queueClient;
    private final ObjectMapper objectMapper;

    public ProcessedEnvelopeNotifier(
        @Qualifier("processed-envelopes") ServiceBusSenderClient queueClient,
        ObjectMapper objectMapper
    ) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
    }

    public void notify(String envelopeId, Long ccdId, EnvelopeCcdAction envelopeCcdAction) {
        try {
            String messageBody =
                objectMapper.writeValueAsString(new ProcessedEnvelope(envelopeId, ccdId, envelopeCcdAction));

            ServiceBusMessage message = new ServiceBusMessage(messageBody);
            message.setContentType(APPLICATION_JSON.toString());
            message.setMessageId(envelopeId);

            queueClient.sendMessage(message);

            log.info("Sent message to processed envelopes queue. Message Body: {}", messageBody);
        } catch (Exception ex) {
            throw new NotificationSendingException(
                "An error occurred when trying to send notification about successfully processed envelope",
                ex
            );
        }
    }
}
