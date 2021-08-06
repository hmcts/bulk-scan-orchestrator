package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.UUID;

import static org.awaitility.Awaitility.await;

@Service
public class ExceptionRecordCreator {

    private final CaseSearcher caseSearcher;
    private final EnvelopeMessager envelopeMessager;

    public ExceptionRecordCreator(
        CaseSearcher caseSearcher,
        EnvelopeMessager envelopeMessager
    ) {
        this.caseSearcher = caseSearcher;
        this.envelopeMessager = envelopeMessager;
    }

    public CaseDetails createExceptionRecord(String container, String resourceName, String fileUrl) throws Exception {
        UUID poBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, fileUrl);

        await("Exception record is created")
            .forever()
            .until(() -> caseSearcher.findExceptionRecord(poBox.toString(), container).isPresent());

        return caseSearcher.findExceptionRecord(poBox.toString(), container).get();
    }
}
