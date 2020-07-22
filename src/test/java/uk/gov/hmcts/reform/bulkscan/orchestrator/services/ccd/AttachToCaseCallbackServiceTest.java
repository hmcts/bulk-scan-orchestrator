package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidationsTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class AttachToCaseCallbackServiceTest {

    private AttachToCaseCallbackService attachToCaseCallbackService;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private ExceptionRecordValidator exceptionRecordValidator;

    @Mock
    private ExceptionRecordFinalizer exceptionRecordFinalizer;

    @Mock
    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    @Mock
    private AttachScannedDocumentsValidator scannedDocumentsValidator;

    private static final String BULKSCAN_ENVELOPE_ID = "some-envelope-id";
    private static final String JURISDICTION = "BULKSCAN";
    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final String CASE_REF = "1539007368674134";
    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";
    private static final String EXISTING_CASE_TYPE = "Bulk_Scanned";

    private ExceptionRecord exceptionRecord;
    private ServiceConfigItem configItem;

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
        .id(Long.parseLong(CASE_REF))
        .data(ImmutableMap.<String, Object>builder()
            .put(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name())
            .put(SEARCH_CASE_REFERENCE, EXISTING_CASE_ID)
            .put(OCR_DATA, asList(ImmutableMap.of("firstName", "John")))
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
        attachToCaseCallbackService = new AttachToCaseCallbackService(
            serviceConfigProvider,
            ccdApi,
            exceptionRecordValidator,
            exceptionRecordFinalizer,
            ccdCaseUpdater,
            paymentsProcessor,
            scannedDocumentsValidator
        );

        exceptionRecord = getExceptionRecord();
        given(exceptionRecordValidator.getValidation(CASE_DETAILS)).willReturn(Validation.valid(exceptionRecord));
        given(ccdApi.getCase(anyString(), anyString())).willReturn(EXISTING_CASE_DETAILS);
        configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(configItem);
    }

    @Test
    void process_should_process_supplementary_evidence_with_ocr() {
        // given
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(ccdCaseUpdater.updateCase(
            exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE
        )).willReturn(new ProcessResult(emptyMap()));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            CASE_DETAILS,
            IDAM_TOKEN,
            USER_ID,
            EVENT_ID_ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();
        verify(ccdCaseUpdater)
            .updateCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE);

        // and
        var paymentsDataCaptor = ArgumentCaptor.forClass(PaymentsHelper.class);
        verify(paymentsProcessor)
            .updatePayments(paymentsDataCaptor.capture(), eq(CASE_REF), eq(JURISDICTION), eq(EXISTING_CASE_ID));
        assertThat(paymentsDataCaptor.getValue()).satisfies(data -> {
            assertThat(data.containsPayments).isEqualTo(CASE_DETAILS.getData().get(CONTAINS_PAYMENTS).equals(YES));
            assertThat(data.envelopeId).isEqualTo(BULKSCAN_ENVELOPE_ID);
        });

        // and exception record should be finalized
        verify(exceptionRecordFinalizer).finalizeExceptionRecord(
            CASE_DETAILS.getData(),
            EXISTING_CASE_ID,
            CcdCallbackType.ATTACHING_SUPPLEMENTARY_EVIDENCE
        );
    }

    @Test
    void process_should_not_update_case_if_error_occurs() {
        // given
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(ccdCaseUpdater.updateCase(
            exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE
        )).willReturn(new ProcessResult(asList("warning1"), asList("error1")));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            CASE_DETAILS,
            IDAM_TOKEN,
            USER_ID,
            EVENT_ID_ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings()).isEqualTo(asList("warning1"));
        assertThat(res.getLeft().getErrors()).isEqualTo(asList("error1"));

        verify(ccdCaseUpdater)
            .updateCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID, EXISTING_CASE_TYPE);
        verifyNoInteractions(paymentsProcessor);
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
}
