package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceUpdaterTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private AttachScannedDocumentsValidator scannedDocumentsValidator;

    private SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;

    private ExceptionRecord exceptionRecord;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private static final String BULKSCAN_ENVELOPE_ID = "some-envelope-id";
    private static final String CASE_REF = "1539007368674134";
    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    @BeforeEach
    public void setUp() {
        supplementaryEvidenceUpdater = new SupplementaryEvidenceUpdater(
            ccdApi,
            scannedDocumentsValidator
        );

        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        configItem.setService(SERVICE_NAME);
    }

    @Test
    void should_update_supplementary_evidence() {
        // given

        // when


        // then

    }

    private static Map<String, Object> document(String filename, String documentNumber) {
        return ImmutableMap.of(
            "value", ImmutableMap.of(
                "fileName", filename,
                "controlNumber", documentNumber,
                "someNumber", 3
            )
        );
    }
}
