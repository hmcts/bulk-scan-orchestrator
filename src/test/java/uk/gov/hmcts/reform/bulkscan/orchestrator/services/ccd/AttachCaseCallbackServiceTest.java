package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidationsTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class AttachCaseCallbackServiceTest {

    private AttachCaseCallbackService attachCaseCallbackService;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private ExceptionRecordValidator exceptionRecordValidator;

    @Mock
    private CcdCaseUpdater ccdCaseUpdater;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    private static final String JURISDICTION = "BULKSCAN";
    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final String CASE_REF = "1539007368674134";
    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";

    private static final Map<String, Object> CASE_DATA = ImmutableMap.of(
        "case_type_id", CASE_TYPE_EXCEPTION_RECORD,
        JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(),
        ATTACH_TO_CASE_REFERENCE, "12345",
        "scanOCRData", asList(ImmutableMap.of("firstName", "John")),
        "scannedDocuments", ImmutableList.of(EXISTING_DOC)
    );

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
        .id(Long.parseLong(CASE_REF))
        .data(CASE_DATA)
        .build();

    @BeforeEach
    void setUp() {
        attachCaseCallbackService = new AttachCaseCallbackService(
            serviceConfigProvider,
            ccdApi,
            exceptionRecordValidator,
            ccdCaseUpdater,
            paymentsProcessor
        );
    }

    @Test
    void process_should_process_supplementary_evidence_with_ocr() {
        // given
        final ExceptionRecord exceptionRecord = getExceptionRecord();
        given(exceptionRecordValidator.getValidation(CASE_DETAILS)).willReturn(Validation.valid(exceptionRecord));
        given(ccdApi.getCase(anyString(), anyString())).willReturn(CaseDetails.builder().data(emptyMap()).build());
        final ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(configItem);
        given(ccdCaseUpdater.updateCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID))
            .willReturn(new ProcessResult(emptyMap()));

        // when
        attachCaseCallbackService.process(
            CASE_DETAILS,
            IDAM_TOKEN,
            USER_ID,
            EVENT_ID_ATTACH_TO_CASE,
            true
        );

        // then
        verify(ccdCaseUpdater).updateCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID, EXISTING_CASE_ID);
        verify(paymentsProcessor).updatePayments(CASE_DETAILS, 12345L);
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
