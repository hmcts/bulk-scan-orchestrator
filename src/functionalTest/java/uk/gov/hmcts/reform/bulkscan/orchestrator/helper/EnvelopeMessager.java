package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.FunctionalQueueConfig;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;

import java.time.Instant;
import java.util.UUID;

@Service
@Import(FunctionalQueueConfig.class)
public class EnvelopeMessager {

    private static final Logger logger = LoggerFactory.getLogger(EnvelopeMessager.class);

    private static final String ENVELOPE_ID_PLACEHOLDER = "{ENVELOPE_ID}";

    @Autowired
    private ServiceBusSenderClient client;

    /**
     * Sends a message, with content from the given file, to the queue.
     *
     * @return Envelope/message ID
     */
    public String sendMessageFromFile(
        String jsonFileName,
        String caseRef,
        String legacyCaseRef,
        String documentUrl
    ) throws JSONException {
        return sendMessageFromFile(
            jsonFileName,
            caseRef,
            legacyCaseRef,
            documentUrl,
            UUID.randomUUID().toString()
        );
    }

    /**
     * Sends a message, with content from the given file, to the queue.
     *
     * @return Envelope/message ID
     */
    public String sendMessageFromFile(
        String jsonFileName,
        String caseRef,
        String legacyCaseRef,
        String documentUrl,
        String envelopeId
    ) throws JSONException {
        String messageContent =
            SampleData.fileContentAsString(jsonFileName)
                .replace(ENVELOPE_ID_PLACEHOLDER, envelopeId);

        JSONObject updateCaseData = new JSONObject(messageContent);

        updateCaseData.put("case_ref", caseRef);
        updateCaseData.put("previous_service_case_ref", legacyCaseRef);

        JSONArray documents = (JSONArray) updateCaseData.get("documents");
        JSONObject document = (JSONObject) documents.get(0);
        document.put("url", documentUrl);
        document.put("uuid", StringUtils.substringAfterLast(documentUrl, "/")); //extract uuid from document url

        ServiceBusMessage message = new ServiceBusMessage(updateCaseData.toString());
        message.setMessageId(envelopeId);
        message.setContentType(MediaType.APPLICATION_JSON_VALUE);
        client.sendMessage(message);

        logger.info(
            "Sent message to queue for the Case ID {} for updating the case. MessageId: {} Current time: {}",
            caseRef,
            message.getMessageId(),
            Instant.now()
        );

        return envelopeId;
    }

}
