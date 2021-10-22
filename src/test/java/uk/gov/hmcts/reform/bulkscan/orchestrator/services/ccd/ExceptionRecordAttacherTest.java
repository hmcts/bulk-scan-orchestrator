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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.Optional;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidatorTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordAttacherTest {

    @Mock
    private SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;

    @Mock
    private SupplementaryEvidenceWithOcrUpdater supplementaryEvidenceWithOcrUpdater;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    @Mock
    private CallbackResultRepositoryProxy callbackResultRepositoryProxy;

    @Mock
    private CcdApi ccdApi;

    private ExceptionRecordAttacher exceptionRecordAttacher;

    private ExceptionRecord exceptionRecord;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String LEGACY_CASE_ID = "23456";
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
            supplementaryEvidenceUpdater,
            supplementaryEvidenceWithOcrUpdater,
            paymentsProcessor,
            callbackResultRepositoryProxy,
            ccdApi
        );

        exceptionRecord = getExceptionRecord();
    }

    @Test
    void should_attach_exception_record_to_case() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        given(supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
            callBackEvent, EXISTING_CASE_DETAILS, EXISTING_CASE_ID, true
        )).willReturn(Optional.empty());

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();

        // and
        var callbackResultCaptor = ArgumentCaptor.forClass(NewCallbackResult.class);
        verify(callbackResultRepositoryProxy).storeCallbackResult(callbackResultCaptor.capture());
        assertThat(callbackResultCaptor.getValue()).satisfies(data -> {
            assertThat(data.requestType).isEqualTo(ATTACH_TO_CASE);
            assertThat(data.exceptionRecordId).isEqualTo(CASE_REF);
            assertThat(data.caseId).isEqualTo(EXISTING_CASE_ID);
        });
        var paymentsDataCaptor = ArgumentCaptor.forClass(PaymentsHelper.class);
        verify(paymentsProcessor)
            .updatePayments(paymentsDataCaptor.capture(), eq(CASE_REF), eq(JURISDICTION), eq(EXISTING_CASE_ID));
        assertThat(paymentsDataCaptor.getValue()).satisfies(data -> {
            assertThat(data.containsPayments).isEqualTo(CASE_DETAILS.getData().get(CONTAINS_PAYMENTS).equals(YES));
            assertThat(data.envelopeId).isEqualTo(BULKSCAN_ENVELOPE_ID);
        });
    }

    @Test
    void should_not_attach_supplementary_evidence_if_case_does_not_exist() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE);
        doThrow(new CaseNotFoundException("msg"))
            .when(supplementaryEvidenceUpdater).updateSupplementaryEvidence(
                callBackEvent,
                EXISTING_CASE_DETAILS,
                EXISTING_CASE_ID
            );

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );

        // then
        verifyNoInteractions(callbackResultRepositoryProxy);
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getErrors()).hasSize(1);
        assertThat(res.getLeft().getErrors().get(0)).isEqualTo("msg");
    }

    @Test
    void should_not_attach_supplementary_evidence_if_exception_thrown() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE);
        doThrow(new IllegalArgumentException("msg"))
            .when(supplementaryEvidenceUpdater).updateSupplementaryEvidence(
                callBackEvent,
                EXISTING_CASE_DETAILS,
                EXISTING_CASE_ID
            );

        // when
        // then
        verifyNoInteractions(callbackResultRepositoryProxy);
        assertThatCode(() -> exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("msg");
    }

    @Test
    void should_not_store_call_back_result_when_not_attach_supplementary_evidence() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE);

        given(supplementaryEvidenceUpdater
            .updateSupplementaryEvidence(
                callBackEvent,
                EXISTING_CASE_DETAILS,
                EXISTING_CASE_ID)
        ).willReturn(false);

        // when
        exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );
        verifyNoInteractions(callbackResultRepositoryProxy);
    }

    @Test
    void should_store_call_back_result_when_attach_supplementary_evidence() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE);

        given(supplementaryEvidenceUpdater
            .updateSupplementaryEvidence(
                callBackEvent,
                EXISTING_CASE_DETAILS,
                EXISTING_CASE_ID)
        ).willReturn(true);

        // when
        exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );
        var callbackResultCaptor = ArgumentCaptor.forClass(NewCallbackResult.class);
        verify(callbackResultRepositoryProxy).storeCallbackResult(callbackResultCaptor.capture());
        assertThat(callbackResultCaptor.getValue()).satisfies(data -> {
            assertThat(data.requestType).isEqualTo(ATTACH_TO_CASE);
            assertThat(data.exceptionRecordId).isEqualTo(CASE_REF);
            assertThat(data.caseId).isEqualTo(EXISTING_CASE_ID);
        });
    }

    @Test
    void should_attach_supplementary_evidence_if_payments_publishing_fails() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        Throwable cause = new Exception("cause");
        doThrow(new PaymentsPublishingException("msg", cause))
            .when(paymentsProcessor).updatePayments(
                any(PaymentsHelper.class),
                eq(CASE_REF),
                eq(JURISDICTION),
                eq(EXISTING_CASE_ID)
            );

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
            callBackEvent,
            CASE_DETAILS,
            true
        );

        // then
        var callbackResultCaptor = ArgumentCaptor.forClass(NewCallbackResult.class);
        verify(callbackResultRepositoryProxy).storeCallbackResult(callbackResultCaptor.capture());
        assertThat(callbackResultCaptor.getValue()).satisfies(data -> {
            assertThat(data.requestType).isEqualTo(ATTACH_TO_CASE);
            assertThat(data.exceptionRecordId).isEqualTo(CASE_REF);
            assertThat(data.caseId).isEqualTo(EXISTING_CASE_ID);
        });
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getErrors()).hasSize(1);
        assertThat(res.getLeft().getErrors().get(0))
            .isEqualTo("Payment references cannot be processed. Please try again later");
    }

    @Test
    void should_return_error_if_exception_record_already_attached_to_case() {
        // given
        CaseDetails caseDetails = CaseDetails.builder()
                .jurisdiction(JURISDICTION)
                .caseTypeId(EXISTING_CASE_TYPE)
                .id(Long.parseLong(EXISTING_CASE_ID))
                .data(Map.of(ATTACH_TO_CASE_REFERENCE, "caseRef"))
                .build();
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(caseDetails);
        AttachToCaseEventData callBackEvent = getCallbackEvent(SUPPLEMENTARY_EVIDENCE_WITH_OCR);

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
                callBackEvent,
                CASE_DETAILS,
                true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings()).isEmpty();
        assertThat(res.getLeft().getErrors()).containsExactly("Exception record is already attached to case caseRef");
    }

    @Test
    void should_return_error_if_wrong_classification() {
        // given
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(NEW_APPLICATION);

        // when
        // then
        assertThatThrownBy(() ->
                exceptionRecordAttacher.tryAttachToCase(
                        callBackEvent,
                        CASE_DETAILS,
                        true
                )
        )
                .isInstanceOf(CallbackException.class)
                .hasMessage("Invalid Journey Classification: NEW_APPLICATION");
    }

    @Test
    void should_store_call_back_result_when_legacy_id() {
        // given
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(
                SUPPLEMENTARY_EVIDENCE,
                EXTERNAL_CASE_REFERENCE,
                LEGACY_CASE_ID
        );
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_ID, SERVICE_NAME))
                .willReturn(singletonList(Long.valueOf(EXISTING_CASE_ID)));

        given(supplementaryEvidenceUpdater
                .updateSupplementaryEvidence(
                        callBackEvent,
                        EXISTING_CASE_DETAILS,
                        EXISTING_CASE_ID
                )
        ).willReturn(true);

        // when
        exceptionRecordAttacher.tryAttachToCase(
                callBackEvent,
                CASE_DETAILS,
                true
        );
        var callbackResultCaptor = ArgumentCaptor.forClass(NewCallbackResult.class);
        verify(callbackResultRepositoryProxy).storeCallbackResult(callbackResultCaptor.capture());
        assertThat(callbackResultCaptor.getValue()).satisfies(data -> {
            assertThat(data.requestType).isEqualTo(ATTACH_TO_CASE);
            assertThat(data.exceptionRecordId).isEqualTo(CASE_REF);
            assertThat(data.caseId).isEqualTo(EXISTING_CASE_ID);
        });
    }

    @Test
    void should_return_error_when_legacy_id_and_case_not_found() {
        // given
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(
                SUPPLEMENTARY_EVIDENCE,
                EXTERNAL_CASE_REFERENCE,
                LEGACY_CASE_ID
        );
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_ID, SERVICE_NAME)).willReturn(emptyList());

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
                callBackEvent,
                CASE_DETAILS,
                true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings()).isEmpty();
        assertThat(res.getLeft().getErrors()).containsExactly("No case found for legacy case reference 23456");
    }

    @Test
    void should_return_error_when_legacy_id_and_multiple_cases_found() {
        // given
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(EXISTING_CASE_DETAILS);
        AttachToCaseEventData callBackEvent = getCallbackEvent(
                SUPPLEMENTARY_EVIDENCE,
                EXTERNAL_CASE_REFERENCE,
                LEGACY_CASE_ID
        );
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_ID, SERVICE_NAME))
                .willReturn(asList(Long.valueOf(EXISTING_CASE_ID), 34567L));

        // when
        Either<ErrorsAndWarnings, String> res = exceptionRecordAttacher.tryAttachToCase(
                callBackEvent,
                CASE_DETAILS,
                true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings()).isEmpty();
        assertThat(res.getLeft().getErrors())
                .containsExactly("Multiple cases (12345, 34567) found for the given legacy case reference: 23456");
    }

    private AttachToCaseEventData getCallbackEvent(Classification classification) {
        return getCallbackEvent(classification, EXISTING_CASE_TYPE, EXISTING_CASE_ID);
    }

    private AttachToCaseEventData getCallbackEvent(
            Classification classification,
            String targetCaseRefType,
            String caseRef
    ) {
        return new AttachToCaseEventData(
            JURISDICTION,
            SERVICE_NAME,
            targetCaseRefType,
            caseRef,
            Long.parseLong(CASE_REF),
            emptyList(),
            IDAM_TOKEN,
            USER_ID,
            classification,
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
                    null,
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
}
