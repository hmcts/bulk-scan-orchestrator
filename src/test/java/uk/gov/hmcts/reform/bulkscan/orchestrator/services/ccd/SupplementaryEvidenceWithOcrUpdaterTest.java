package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceWithOcrUpdaterTest {

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private CcdCaseUpdater ccdCaseUpdater;

    private SupplementaryEvidenceWithOcrUpdater supplementaryEvidenceWithOcrUpdater;

    private ExceptionRecord exceptionRecord;
    private AttachToCaseEventData callBackEvent;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private static final String CASE_REF = "1539007368674134";

    private static final CaseDetails EXISTING_CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(EXISTING_CASE_TYPE)
        .id(Long.parseLong(EXISTING_CASE_ID))
        .data(emptyMap())
        .build();

    @BeforeEach
    void setUp() {
        supplementaryEvidenceWithOcrUpdater = new SupplementaryEvidenceWithOcrUpdater(
            serviceConfigProvider,
            ccdApi,
            ccdCaseUpdater
        );

        exceptionRecord = getExceptionRecord();
        callBackEvent = getCallbackEvent();
    }

    @Test
    void should_update_case_if_properly_configured() {
        // given
        ServiceConfigItem configItem = getConfigItem("url");
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(configItem);
        given(ccdApi.getCase(EXISTING_CASE_ID, JURISDICTION)).willReturn(EXISTING_CASE_DETAILS);
        Optional<ErrorsAndWarnings> errorsAndWarnings = Optional.of(new ErrorsAndWarnings(emptyList(), emptyList()));
        given(ccdCaseUpdater.updateCase(
            exceptionRecord,
            SERVICE_NAME,
            true,
            IDAM_TOKEN,
            USER_ID,
            EXISTING_CASE_ID,
            EXISTING_CASE_TYPE
        )).willReturn(errorsAndWarnings);

        // when
        Optional<ErrorsAndWarnings> res = supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
            callBackEvent,
            EXISTING_CASE_ID,
            true);

        // then
        assertThat(res).isSameAs(errorsAndWarnings);
    }

    @Test
    void should_not_update_case_if_service_is_not_configured() {
        // given
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(null);

        // when
        // then
        assertThatCode(() ->
            supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
                callBackEvent,
                EXISTING_CASE_ID,
                true
            ))
            .isInstanceOf(CallbackException.class)
            .hasMessage("Update URL is not configured");

        verifyNoInteractions(ccdApi, ccdCaseUpdater);
    }

    @Test
    void should_not_update_case_if_update_url_is_null() {
        // given
        ServiceConfigItem configItem = getConfigItem(null);
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(configItem);
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(null);

        // when
        // then
        assertThatCode(() ->
            supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
                callBackEvent,
                EXISTING_CASE_ID,
                true
            ))
            .isInstanceOf(CallbackException.class)
            .hasMessage("Update URL is not configured");

        verifyNoInteractions(ccdApi, ccdCaseUpdater);
    }

    @Test
    void should_not_update_case_if_update_url_is_empty() {
        // given
        ServiceConfigItem configItem = getConfigItem("");
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(configItem);
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(null);

        // when
        // then
        assertThatCode(() ->
            supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
                callBackEvent,
                EXISTING_CASE_ID,
                true
            ))
            .isInstanceOf(CallbackException.class)
            .hasMessage("Update URL is not configured");

        verifyNoInteractions(ccdApi, ccdCaseUpdater);
    }

    private AttachToCaseEventData getCallbackEvent() {
        return new AttachToCaseEventData(
            JURISDICTION,
            SERVICE_NAME,
            EXISTING_CASE_TYPE,
            EXISTING_CASE_ID,
            Long.parseLong(CASE_REF),
            emptyList(),
            IDAM_TOKEN,
            USER_ID,
            SUPPLEMENTARY_EVIDENCE,
            exceptionRecord
        );
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            "1",
            "caseTypeId",
            "envelopeId123",
            "12345",
            "some jurisdiction",
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "Form1",
            now(),
            now(),
            singletonList(new ScannedDocument(
                DocumentType.FORM,
                "D8",
                new DocumentUrl(
                    "http://locahost",
                    "http://locahost/binary",
                    "file1.pdf"
                ),
                "1234",
                "file1.pdf",
                now(),
                now()
            )),
            emptyList()
        );
    }

    private ServiceConfigItem getConfigItem(String updateUrl) {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setUpdateUrl(updateUrl);
        configItem.setService(SERVICE_NAME);
        return configItem;
    }
}
