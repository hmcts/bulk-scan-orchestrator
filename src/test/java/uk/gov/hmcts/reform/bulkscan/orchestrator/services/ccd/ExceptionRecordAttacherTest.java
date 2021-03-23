package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordAttacherTest {

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private AttachScannedDocumentsValidator scannedDocumentsValidator;

    private ExceptionRecordAttacher exceptionRecordAttacher;

    private ExceptionRecord exceptionRecord;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private static final CaseDetails EXISTING_CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(EXISTING_CASE_TYPE)
        .id(Long.parseLong(EXISTING_CASE_ID))
        .data(emptyMap())
        .build();

    @BeforeEach
    void setUp() {
        exceptionRecordAttacher = new ExceptionRecordAttacher(
            serviceConfigProvider,
            paymentsProcessor,
            ccdApi,
            ccdCaseUpdater,
            scannedDocumentsValidator
        );

        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        configItem.setService(SERVICE_NAME);
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(configItem);
    }

    @Test
    void tryAttachToCase() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        given(ccdCaseUpdater.updateCase(
            exceptionRecord, SERVICE_NAME, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE
        )).willReturn(Optional.empty());
        AttachToCaseEventData callBackEvent = new AttachToCaseEventData(
            "BULKSCAN",
            "bulkscan",
            EXISTING_CASE_TYPE,
            EXISTING_CASE_ID,
            1L,
            emptyList(),
            IDAM_TOKEN,
            USER_ID,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            exceptionRecord
        );

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            EXISTING_CASE_DETAILS,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();
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
