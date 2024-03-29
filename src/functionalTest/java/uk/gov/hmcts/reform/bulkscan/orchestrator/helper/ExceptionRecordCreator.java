package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Service
public class ExceptionRecordCreator {

    private final CaseSearcher caseSearcher;
    private final JmsEnvelopeMessager jmsEnvelopeMessager;
    private final EnvelopeMessager envelopeMessager;

    public ExceptionRecordCreator(
        CaseSearcher caseSearcher,
        JmsEnvelopeMessager jmsEnvelopeMessager,
        EnvelopeMessager envelopeMessager
    ) {
        this.caseSearcher = caseSearcher;
        this.jmsEnvelopeMessager = jmsEnvelopeMessager;
        this.envelopeMessager = envelopeMessager;
    }

    public CaseDetails createExceptionRecord(String container, String resourceName, String fileUrl) throws Exception {
        String envelopeId = !Boolean.parseBoolean(System.getenv("JMS_ENABLED"))
            ? envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, fileUrl)
            : jmsEnvelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, fileUrl);

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> caseSearcher.findExceptionRecord(envelopeId, container).isPresent());

        return caseSearcher.findExceptionRecord(envelopeId, container).get();
    }
}
