package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;

import static java.util.Collections.emptyList;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class PaymentToExistingCaseTest {

    @Value("${test-url}")
    private String testUrl;

    @Value("${document_management.url}")
    private String documentManagementUrl;

    @Value("${document_management.context-path}")
    private String dmContextPath;

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Autowired
    private CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private String dmUrl;

    private String documentUuid;

    @BeforeEach
    void setup() throws Exception {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
        documentUuid = StringUtils.substringAfterLast(dmUrl, "/");
    }

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_no_evidence() throws Exception {
        //given
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now(), ImmutableMap.of("awaitingPaymentDCNProcessing", "Yes"));

        // when
        // message sent to payments queue

        //then
    }
}
