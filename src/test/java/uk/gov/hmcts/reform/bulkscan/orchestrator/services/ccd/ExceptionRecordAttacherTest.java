package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidationsTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordAttacherTest {

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private CcdCaseUpdater ccdCaseUpdater;

    private ExceptionRecordAttacher exceptionRecordAttacher;

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

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
        .id(Long.parseLong(CASE_REF))
        .data(ImmutableMap.<String, Object>builder()
            .put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name())
            .put(SEARCH_CASE_REFERENCE, EXISTING_CASE_ID)
            .put(OCR_DATA, singletonList(ImmutableMap.of("firstName", "John")))
            .put(SCANNED_DOCUMENTS, ImmutableList.of(EXISTING_DOC))
            .put(CONTAINS_PAYMENTS, YES)
            .put(ENVELOPE_ID, BULKSCAN_ENVELOPE_ID)
            .build()
        )
        .build();
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
            supplementaryEvidenceUpdater,
            paymentsProcessor,
            ccdApi,
            ccdCaseUpdater
        );

        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        configItem.setService(SERVICE_NAME);
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(configItem);
    }

    @Test
    void should_attach_exception_record_to_case() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        given(ccdCaseUpdater.updateCase(
            exceptionRecord, SERVICE_NAME, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE
        )).willReturn(Optional.empty());
        AttachToCaseEventData callBackEvent = getCallbackEvent();

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();
        verify(ccdCaseUpdater)
            .updateCase(exceptionRecord, SERVICE_NAME, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE);

        // and
        var paymentsDataCaptor = ArgumentCaptor.forClass(PaymentsHelper.class);
        verify(paymentsProcessor)
            .updatePayments(paymentsDataCaptor.capture(), eq(CASE_REF), eq(JURISDICTION), eq(EXISTING_CASE_ID));
        assertThat(paymentsDataCaptor.getValue()).satisfies(data -> {
            assertThat(data.containsPayments).isEqualTo(CASE_DETAILS.getData().get(CONTAINS_PAYMENTS).equals(YES));
            assertThat(data.envelopeId).isEqualTo(BULKSCAN_ENVELOPE_ID);
        });
    }

    @Test
    void should_not_attach_exception_record_if_error_occurs() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        given(ccdCaseUpdater.updateCase(
            exceptionRecord, SERVICE_NAME, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE
        )).willReturn(Optional.empty());
        AttachToCaseEventData callBackEvent = getCallbackEvent();

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();
        verify(ccdCaseUpdater)
            .updateCase(exceptionRecord, SERVICE_NAME, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE);

        // and
        var paymentsDataCaptor = ArgumentCaptor.forClass(PaymentsHelper.class);
        verify(paymentsProcessor)
            .updatePayments(paymentsDataCaptor.capture(), eq(CASE_REF), eq(JURISDICTION), eq(EXISTING_CASE_ID));
        assertThat(paymentsDataCaptor.getValue()).satisfies(data -> {
            assertThat(data.containsPayments).isEqualTo(CASE_DETAILS.getData().get(CONTAINS_PAYMENTS).equals(YES));
            assertThat(data.envelopeId).isEqualTo(BULKSCAN_ENVELOPE_ID);
        });
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
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            exceptionRecord
        );
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
