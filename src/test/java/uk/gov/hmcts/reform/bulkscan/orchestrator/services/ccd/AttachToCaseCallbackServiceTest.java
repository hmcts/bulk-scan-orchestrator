package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import io.vavr.control.Validation;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidatorTest.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CONTAINS_PAYMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ENVELOPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class AttachToCaseCallbackServiceTest {

    public static final String WRONG_CASE_TYPE_ID = "BULKSCAN_Exception";

    private AttachToCaseCallbackService attachToCaseCallbackService;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private ExceptionRecordValidator exceptionRecordValidator;

    @Mock
    private ExceptionRecordFinalizer exceptionRecordFinalizer;

    @Mock
    private ExceptionRecordAttacher exceptionRecordAttacher;

    @Mock
    private CallbackValidator callbackValidator;

    @Mock
    private EventIdValidator eventIdValidator;

    private static final String BULKSCAN_ENVELOPE_ID = "some-envelope-id";
    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE_NAME = "bulkscan";
    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final Long CASE_REF = 1539007368674134L;
    private static final String DOCUMENT_FILENAME = "document.pdf";
    private static final String DOCUMENT_NUMBER = "123456";
    private static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER);

    private static final String IDAM_TOKEN = "IDAM_TOKEN";
    private static final String USER_ID = "USER_ID";
    private static final String EXISTING_CASE_ID = "12345";

    private static final String PREREQUISITES_ERROR = "prerequisites_error";
    private static final String ERROR_1 = "error1";
    private static final String WARNING_1 = "warning1";

    private ExceptionRecord exceptionRecord;
    private ServiceConfigItem configItem;

    @BeforeEach
    void setUp() {
        attachToCaseCallbackService = new AttachToCaseCallbackService(
            serviceConfigProvider,
            exceptionRecordValidator,
            exceptionRecordFinalizer,
            exceptionRecordAttacher,
            callbackValidator,
            eventIdValidator
        );

        exceptionRecord = getExceptionRecord();
        configItem = new ServiceConfigItem();
        configItem.setUpdateUrl("url");
        configItem.setService(SERVICE_NAME);
    }

    @Test
    void process_should_process_supplementary_evidence_with_ocr() {
        // given
        CaseDetails caseDetails = getValidCaseDetails();
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(exceptionRecordAttacher.tryAttachToCase(
            any(AttachToCaseEventData.class),
            any(CaseDetails.class),
            anyBoolean()
        ))
            .willReturn(Either.right(EXISTING_CASE_ID));
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(configItem);
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        any(ServiceConfigItem.class)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isRight()).isTrue();

        // and exception record should be finalized
        verify(exceptionRecordFinalizer).finalizeExceptionRecord(
            caseDetails.getData(),
            EXISTING_CASE_ID,
            CcdCallbackType.ATTACHING_SUPPLEMENTARY_EVIDENCE
        );
    }

    @Test
    void process_should_not_update_case_if_mandatory_prerequisites_invalid() {
        // given
        CaseDetails caseDetails = getValidCaseDetails();
        given(exceptionRecordValidator.mandatoryPrerequisites(any()))
            .willReturn(Validation.invalid(PREREQUISITES_ERROR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getErrors()).isEqualTo(singletonList(PREREQUISITES_ERROR));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_error_occurs() {
        // given
        CaseDetails caseDetails = getValidCaseDetails();
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(exceptionRecordAttacher.tryAttachToCase(
            any(AttachToCaseEventData.class),
            any(CaseDetails.class),
            anyBoolean())
        )
            .willReturn(Either.left(new ErrorsAndWarnings(singletonList(ERROR_1), singletonList(WARNING_1))));
        given(serviceConfigProvider.getConfig(SERVICE_NAME)).willReturn(configItem);
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        any(ServiceConfigItem.class)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings()).isEqualTo(singletonList(WARNING_1));
        assertThat(res.getLeft().getErrors()).isEqualTo(singletonList(ERROR_1));
    }

    @Test
    void process_should_not_update_case_if_null_jurisdiction() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class)))
                .willReturn(Validation.invalid("Internal Error: invalid jurisdiction supplied: null"));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        eq(null)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Internal Error: invalid jurisdiction supplied: null"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_wrong_case_type_id() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            WRONG_CASE_TYPE_ID,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.invalid("Case type ID (BULKSCAN_Exception) has invalid format"));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Case type ID (" + WRONG_CASE_TYPE_ID + ") has invalid format"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_null_case_type_id() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            null,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.invalid("No case type ID supplied"));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("No case type ID supplied"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_null_id() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            null,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class)))
                .willReturn(Validation.invalid("Exception case has no Id"));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        eq(null)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Exception case has no Id"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_null_idam_token() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(any()))
                .willReturn(Validation.invalid("Callback has no Idam token received in the header"));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        eq(null)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            null,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Callback has no Idam token received in the header"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_null_user_id() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
        given(exceptionRecordValidator.getValidation(caseDetails)).willReturn(Validation.valid(exceptionRecord));
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(any()))
                .willReturn(Validation.invalid("Callback has no user id received in the header"));
        given(callbackValidator
                .validatePayments(
                        any(CaseDetails.class),
                        any(Classification.class),
                        eq(null)
                )
        ).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.valid(SUPPLEMENTARY_EVIDENCE_WITH_OCR));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            null,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Callback has no user id received in the header"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_null_journey_classification() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            null
        );
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.invalid("Missing journeyClassification"));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors())
            .isEqualTo(singletonList("Missing journeyClassification"));
        verifyNoInteractions(exceptionRecordAttacher);
    }

    @Test
    void process_should_not_update_case_if_wrong_journey_classification() {
        // given
        CaseDetails caseDetails = getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            NEW_APPLICATION.name()
        );
        given(exceptionRecordValidator.mandatoryPrerequisites(any())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJurisdiction(any(CaseDetails.class))).willReturn(Validation.valid(JURISDICTION));
        given(callbackValidator.hasTargetCaseReference(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasAnId(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasServiceNameInCaseTypeId(any(CaseDetails.class)))
                .willReturn(Validation.valid(SERVICE_NAME));
        given(callbackValidator.hasAScannedRecord(any(CaseDetails.class))).willReturn(Validation.valid(null));
        given(callbackValidator.hasIdamToken(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasUserId(anyString())).willReturn(Validation.valid(null));
        given(callbackValidator.hasJourneyClassificationForAttachToCase(any(CaseDetails.class)))
                .willReturn(Validation.invalid("The current Journey Classification NEW_APPLICATION "
                        + "is not allowed for attaching to case"));

        // when
        Either<ErrorsAndWarnings, Map<String, Object>> res = attachToCaseCallbackService.process(
            caseDetails,
            IDAM_TOKEN,
            USER_ID,
            EventIds.ATTACH_TO_CASE,
            true
        );

        // then
        assertThat(res.isLeft()).isTrue();
        assertThat(res.getLeft().getWarnings().isEmpty()).isTrue();
        assertThat(res.getLeft().getErrors()).isEqualTo(singletonList(
            "The current Journey Classification " + NEW_APPLICATION.name() + " is not allowed for attaching to case")
        );
        verifyNoInteractions(exceptionRecordAttacher);
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

    private CaseDetails getValidCaseDetails() {
        return getCaseDetails(
            JURISDICTION,
            CASE_TYPE_EXCEPTION_RECORD,
            CASE_REF,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR.name()
        );
    }

    private CaseDetails getCaseDetails(
        String jurisdiction,
        String caseTypeId,
        Long id,
        String journeyClassification
    ) {
        Map<String, Object> dataMap = new HashMap<>();
        if (journeyClassification != null) {
            dataMap.put(JOURNEY_CLASSIFICATION, journeyClassification);
        }
        dataMap.put(SEARCH_CASE_REFERENCE, EXISTING_CASE_ID);
        dataMap.put(OCR_DATA, singletonList(ImmutableMap.of("firstName", "John")));
        dataMap.put(SCANNED_DOCUMENTS, ImmutableList.of(EXISTING_DOC));
        dataMap.put(CONTAINS_PAYMENTS, YES);
        dataMap.put(ENVELOPE_ID, BULKSCAN_ENVELOPE_ID);

        return CaseDetails.builder()
            .jurisdiction(jurisdiction)
            .caseTypeId(caseTypeId)
            .id(id)
            .data(dataMap)
            .build();
    }
}
