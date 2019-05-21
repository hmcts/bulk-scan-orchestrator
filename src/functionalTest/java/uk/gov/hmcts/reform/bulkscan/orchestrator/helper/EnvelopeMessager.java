package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
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

    @Autowired
    private QueueClient client;

    public void sendMessageFromFile(
        String jsonFileName,
        String caseRef,
        UUID poBox,
        String documentUrl
    ) throws JSONException, InterruptedException, ServiceBusException {
        JSONObject updateCaseData = new JSONObject(SampleData.fileContentAsString(jsonFileName));
        updateCaseData.put("case_ref", caseRef);

        if (poBox != null) {
            updateCaseData.put("po_box", poBox);
        }

        JSONArray documents = (JSONArray) updateCaseData.get("documents");
        JSONObject document = (JSONObject) documents.get(0);
        document.put("url", documentUrl);
        document.put("uuid", StringUtils.substringAfterLast(documentUrl, "/")); //extract uuid from document url

        Message message = new Message(
            UUID.randomUUID().toString(),
            updateCaseData.toString(),
            MediaType.APPLICATION_JSON_UTF8_VALUE
        );
        client.send(message);

        logger.info(
            "Sent message to queue for the Case ID {} for updating the case. MessageId: {} Current time: {}",
            caseRef,
            message.getMessageId(),
            Instant.now()
        );
    }

}
