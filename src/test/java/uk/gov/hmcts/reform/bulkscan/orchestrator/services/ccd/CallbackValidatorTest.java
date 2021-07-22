package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithAwaitingPaymentsAndClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class CallbackValidatorTest {
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final String NO_CASE_TYPE_ID_SUPPLIED_ERROR = "No case type ID supplied";

    private static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    private static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";
    private static final String NO_IDAM_TOKEN_RECEIVED_ERROR = "Callback has no Idam token received in the header";
    private static final String NO_USER_ID_RECEIVED_ERROR = "Callback has no user id received in the header";

    @Mock
    private CaseReferenceValidator caseReferenceValidator;

    @Mock
    private ScannedDocumentValidator scannedDocumentValidator;

    private CallbackValidator callbackValidator;

    @BeforeEach
    void setUp() {
        callbackValidator = new CallbackValidator(caseReferenceValidator, scannedDocumentValidator);
    }

    @Test
    void hasCaseTypeId_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .caseTypeId(CASE_TYPE_ID)
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasCaseTypeId(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo(CASE_TYPE_ID);
    }

    @Test
    void hasCaseTypeId_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasCaseTypeId(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Missing caseType");
    }

    @Test
    void hasFormType_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .data(Map.of("formType", "B123"))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasFormType(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo("B123");
    }

    @Test
    void hasFormType_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .data(emptyMap())
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasFormType(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Missing Form Type");
    }

    @Test
    void hasJurisdiction_returns_valid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
                .jurisdiction("some jurisdiction")
        );

        // when
        Validation<String, String> res = callbackValidator.hasJurisdiction(caseDetails);

        // then
        assertThat(res.isValid()).isTrue();
        assertThat(res.get()).isEqualTo("some jurisdiction");
    }

    @Test
    void hasJurisdiction_returns_invalid() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
                .id(Long.valueOf(CASE_ID))
        );

        // when
        Validation<String, String> res = callbackValidator.hasJurisdiction(caseDetails);

        // then
        assertThat(res.isValid()).isFalse();
        assertThat(res.getError()).isEqualTo("Internal Error: invalid jurisdiction supplied: null");
    }

    @Test
    void hasTargetCaseReference_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateTargetCaseReference(any(CaseDetails.class))).willReturn(validationRes);

        // when
        Validation<String, String> res = callbackValidator.hasTargetCaseReference(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void hasSearchCaseReference_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateSearchCaseReferenceWithSearchType(any(CaseDetails.class)))
                .willReturn(validationRes);

        // when
        Validation<String, String> res = callbackValidator.hasSearchCaseReference(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void hasSearchCaseReferenceType_calls_caseReferenceValidator() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, String> validationRes = Validation.valid("caseRef");
        given(caseReferenceValidator.validateCaseReferenceType(any(CaseDetails.class))).willReturn(validationRes);

        // when
        Validation<String, String> res = callbackValidator.hasSearchCaseReferenceType(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    @Test
    void noCaseIdTest() {
        checkValidation(
                createCaseWith(b -> b.id(null)),
                false,
                null,
                callbackValidator::hasAnId,
                "Exception case has no Id"
        );
    }

    @Test
    void valid_case_id_should_pass() {
        checkValidation(
                createCaseWith(b -> b.id(1L)),
                true,
                1L,
                callbackValidator::hasAnId,
                null
        );
    }

    @Test
    void no_case_details_should_fail() {
        checkValidation(
                null,
                false,
                1L,
                callbackValidator::hasAnId,
                "Exception case has no Id"
        );
    }


    @Test
    void hasAScannedRecord_calls_scannedDocumentValidatorr() {
        // given
        CaseDetails caseDetails = mock(CaseDetails.class);
        Validation<String, List<Map<String,Object>>> validationRes = Validation.valid(emptyList());
        given(scannedDocumentValidator.validate(any(CaseDetails.class))).willReturn(validationRes);

        // when
        Validation<?, ?> res = callbackValidator.hasAScannedRecord(caseDetails);

        // then
        assertThat(res).isSameAs(validationRes);
    }

    private static Object[][] caseTypeIdTestParams() {
        return new Object[][] {
                {"null case details", null, false, NO_CASE_TYPE_ID_SUPPLIED_ERROR},
                {"null case type ID", createCaseWith(b -> b.data(null)), false, NO_CASE_TYPE_ID_SUPPLIED_ERROR},
                {"case type ID with wrong suffix", createCaseWith(b -> b.caseTypeId("service_exceptionrecord")), false, "Case type ID (service_exceptionrecord) has invalid format"},
                {"case type ID being just sufifx", createCaseWith(b -> b.caseTypeId("_ExceptionRecord")), false, "Case type ID (_ExceptionRecord) has invalid format"},
                {"valid case type ID", createCaseWith(b -> b.caseTypeId("SERVICE_ExceptionRecord")), true, "service"},
                {"case type ID with underscores", createCaseWith(b -> b.caseTypeId("LONG_SERVICE_NAME_ExceptionRecord")), true, "long_service_name"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("caseTypeIdTestParams")
    @DisplayName("Should accept valid case type ID")
    void serviceNameInCaseTypeIdTest(
            String caseDescription,
            CaseDetails inputCase,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                inputCase,
                valid,
                expectedValueOrError,
                callbackValidator::hasServiceNameInCaseTypeId,
                expectedValueOrError
        );
    }

    private static Object[][] classificationTestParams() {
        return new Object[][]{
                {"Invalid journey classification", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, "invalid_classification"))), false, "Invalid journey classification invalid_classification"},
                {"Valid journey classification(supplementary evidence)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE.name()))), true, null},
                {"Valid journey classification(supplementary evidence with ocr)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(), OCR_DATA, asList(ImmutableMap.of("f1", "v1"))))), true, null},
                {"Valid journey classification(supplementary evidence with ocr ocr empty)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(), OCR_DATA, emptyList()))), false, "The 'attach to case' event is not supported for supplementary evidence with OCR but not containing OCR data"},
                {"Valid journey classification(exception without ocr)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, CLASSIFICATION_EXCEPTION))), true, null},
                {"Valid journey classification(exception with empty ocr list)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, CLASSIFICATION_EXCEPTION, OCR_DATA, emptyList()))), true, null},
                {"Invalid action-Valid journey classification(exception with ocr)", createCaseWith(b -> b.data(caseDataWithOcr())), false, "The 'attach to case' event is not supported for exception records with OCR"}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("classificationTestParams")
    void canBeAttachedToCaseTest(
            String caseDescription,
            CaseDetails inputCase,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                inputCase,
                valid,
                expectedValueOrError,
                callbackValidator::canBeAttachedToCase,
                expectedValueOrError
        );
    }

    private static Object[][] idamTokenTestParams() {
        return new Object[][]{
                {"null idam token", null, false, NO_IDAM_TOKEN_RECEIVED_ERROR},
                {"valid idam token", "valid token", true, "valid token"}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("idamTokenTestParams")
    @DisplayName("Should have idam token in the request")
    void idamTokenInTheRequestTest(
            String caseDescription,
            String input,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                input,
                valid,
                expectedValueOrError,
                callbackValidator::hasIdamToken,
                expectedValueOrError
        );
    }

    private static Object[][] userIdTestParams() {
        return new Object[][]{
                {"null user id", null, false, NO_USER_ID_RECEIVED_ERROR},
                {"valid user id", "valid user id", true, "valid user id"}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("userIdTestParams")
    @DisplayName("Should have user id in the request")
    void userIdInTheRequestTest(
            String caseDescription,
            String input,
            boolean valid,
            String expectedValueOrError
    ) {
        checkValidation(
                input,
                valid,
                expectedValueOrError,
                callbackValidator::hasUserId,
                expectedValueOrError
        );
    }

    private static Object[][] attachToCaseWithPaymentsTestParams() {
        String pendingPaymentsProcessing = "Cannot attach this exception record to a case because it has pending payments";
        return new Object[][]{
                {"Valid supplementary evidence with no pending payments", caseWithAwaitingPaymentsAndClassification("No", SUPPLEMENTARY_EVIDENCE.toString()), SUPPLEMENTARY_EVIDENCE, singletonList(SUPPLEMENTARY_EVIDENCE), true, null},
                {"Valid allow supplementary evidence with pending payments", caseWithAwaitingPaymentsAndClassification("Yes", SUPPLEMENTARY_EVIDENCE.toString()), SUPPLEMENTARY_EVIDENCE, singletonList(SUPPLEMENTARY_EVIDENCE), true, null},
                {"Invalid supplementary evidence with pending payments", caseWithAwaitingPaymentsAndClassification("Yes", SUPPLEMENTARY_EVIDENCE.toString()), SUPPLEMENTARY_EVIDENCE, emptyList(), false, pendingPaymentsProcessing},
                {"Valid supplementary evidence with ocr no pending payments", caseWithAwaitingPaymentsAndClassification("No", SUPPLEMENTARY_EVIDENCE_WITH_OCR.toString()), SUPPLEMENTARY_EVIDENCE_WITH_OCR, asList(SUPPLEMENTARY_EVIDENCE, SUPPLEMENTARY_EVIDENCE_WITH_OCR), true, null},
                {"Valid allow supplementary evidence with ocr with pending payments", caseWithAwaitingPaymentsAndClassification("Yes", SUPPLEMENTARY_EVIDENCE_WITH_OCR.toString()), SUPPLEMENTARY_EVIDENCE_WITH_OCR, singletonList(SUPPLEMENTARY_EVIDENCE_WITH_OCR), true, null},
                {"Invalid supplementary evidence with ocr with pending payments", caseWithAwaitingPaymentsAndClassification("Yes", SUPPLEMENTARY_EVIDENCE_WITH_OCR.toString()), SUPPLEMENTARY_EVIDENCE_WITH_OCR, emptyList(), false, pendingPaymentsProcessing},
                {"Invalid awaiting payments dcn processing yes", caseWithAwaitingPaymentsAndClassification("Yes", EXCEPTION.toString()), EXCEPTION, singletonList(SUPPLEMENTARY_EVIDENCE), false, pendingPaymentsProcessing},
                {"Valid awaiting payments dcn processing No", caseWithAwaitingPaymentsAndClassification("No", EXCEPTION.toString()), EXCEPTION, singletonList(EXCEPTION), true, null},
                {"Valid awaiting payments dcn processing Yes", caseWithAwaitingPaymentsAndClassification("Yes", EXCEPTION.toString()), EXCEPTION, singletonList(EXCEPTION), true, null},
                {"Valid awaiting payments dcn processing null", caseWithAwaitingPaymentsAndClassification(null, EXCEPTION.toString()), EXCEPTION, emptyList(), true, null}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{4} error:{5}")
    @MethodSource("attachToCaseWithPaymentsTestParams")
    @DisplayName("Should attach to case when allowed with pending payments")
    void attachToCaseWithPaymentsTest(String caseReason, CaseDetails input, Classification classification, List<Classification> classifications, boolean valid, String expectedValueOrError) {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications(classifications);
        checkValidation(
                input,
                valid,
                expectedValueOrError,
                (caseDetails -> callbackValidator.validatePayments(caseDetails, classification, configItem)),
                expectedValueOrError
        );
    }

    private static ImmutableMap<String, Object> caseDataWithOcr() {
        return ImmutableMap.of(
                JOURNEY_CLASSIFICATION, CLASSIFICATION_EXCEPTION,
                OCR_DATA, singletonList(
                        ImmutableMap.of("first_name", "John")
                )
        );
    }

    private <T> void checkValidation(
            CaseDetails input,
            boolean valid,
            T realValue,
            Function<CaseDetails, Validation<String, ?>> validationMethod,
            String errorString
    ) {
        Validation<String, ?> validation = validationMethod.apply(input);

        softAssertions(valid, realValue, errorString, validation);
    }

    private <T> void checkValidation(
            String input,
            boolean valid,
            T realValue,
            Function<String, Validation<String, ?>> validationMethod,
            String errorString
    ) {
        Validation<String, ?> validation = validationMethod.apply(input);

        softAssertions(valid, realValue, errorString, validation);
    }

    private <T> void softAssertions(boolean valid, T realValue, String errorString, Validation<String, ?> validation) {
        if (valid) {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isTrue();
                softly.assertThat(validation.get()).isEqualTo(realValue);
            });
        } else {
            assertSoftly(softly -> {
                softly.assertThat(validation.isValid()).isFalse();
                softly.assertThat(validation.getError()).isEqualTo(errorString);
            });
        }
    }
}
