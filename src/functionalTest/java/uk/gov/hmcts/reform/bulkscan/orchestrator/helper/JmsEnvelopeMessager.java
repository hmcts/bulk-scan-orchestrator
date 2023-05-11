package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.policy.JmsDefaultRedeliveryPolicy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;

import java.time.Instant;
import java.util.UUID;
import javax.jms.ConnectionFactory;

@Service
public class JmsEnvelopeMessager {

    private static final Logger logger = LoggerFactory.getLogger(JmsEnvelopeMessager.class);

    private static final String ENVELOPE_ID_PLACEHOLDER = "{ENVELOPE_ID}";
    private static final JmsTemplate JMS_TEMPLATE = new JmsTemplate();

    public JmsEnvelopeMessager() {
        // Set the connection factory only once in the constructor
        JMS_TEMPLATE.setConnectionFactory(getTestFactory());
        JMS_TEMPLATE.setReceiveTimeout(5000); // Set the receive timeout to 5 seconds
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

        JMS_TEMPLATE.convertAndSend("envelopes", updateCaseData.toString());

        logger.info(
            "Sent message to JMS queue for the Case ID {} for updating the case. Content: {} Current time: {}",
            caseRef,
            updateCaseData.toString(),
            Instant.now()
        );

        return envelopeId;
    }

    public ConnectionFactory getTestFactory() {
        String connection = String.format("amqp://localhost:%1s?amqp.idleTimeout=%2d", "5672", 30000);
        JmsConnectionFactory jmsConnectionFactory = new JmsConnectionFactory(connection);
        jmsConnectionFactory.setUsername("admin");
        jmsConnectionFactory.setPassword("admin");
        JmsDefaultRedeliveryPolicy jmsDefaultRedeliveryPolicy = new JmsDefaultRedeliveryPolicy();
        jmsDefaultRedeliveryPolicy.setMaxRedeliveries(3);
        jmsConnectionFactory.setRedeliveryPolicy(jmsDefaultRedeliveryPolicy);
        jmsConnectionFactory.setClientID(UUID.randomUUID().toString());
        return new CachingConnectionFactory(jmsConnectionFactory);
    }
}
