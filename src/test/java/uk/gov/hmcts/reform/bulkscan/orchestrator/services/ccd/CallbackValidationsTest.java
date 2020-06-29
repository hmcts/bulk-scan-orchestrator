package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithAttachReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithAwaitingPaymentsAndClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithCcdSearchCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithDocument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithExternalSearchCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithSearchCaseRefTypeAndCaseRef;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithSearchCaseReferenceType;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.caseWithTargetReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.createCaseWith;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.TestCaseBuilder.document;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@SuppressWarnings("checkstyle:LineLength")
class CallbackValidationsTest {

    public static final String NO_CASE_TYPE_ID_SUPPLIED_ERROR = "No case type ID supplied";
    public static final String NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR = "No case reference type supplied";
    public static final String NO_IDAM_TOKEN_RECEIVED_ERROR = "Callback has no Idam token received in the header";
    public static final String NO_USER_ID_RECEIVED_ERROR = "Callback has no user id received in the header";
    public static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    public static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";

    private static Object[][] attachToCaseReferenceTestParams() {
        String noReferenceSupplied = "No case reference supplied";
        return new Object[][]{
            {"generic non number removal", caseWithAttachReference("£1234234393"), true, "1234234393"},
            {"- removal", caseWithAttachReference("1234-234-393"), true, "1234234393"},
            {"space removal", caseWithAttachReference("1234 234 393"), true, "1234234393"},
            {"prefix and post fix spaces removal", caseWithAttachReference("  AH 234 393 "), true, "234393"},
            {"No numbers supplied", caseWithAttachReference("#"), false, "Invalid case reference: '#'"},
            {"empty string", caseWithAttachReference(""), false, "Invalid case reference: ''"},
            {"null case details", null, false, noReferenceSupplied},
            {"null data", createCaseWith(b -> b.data(null)), false, noReferenceSupplied},
            {"empty data", createCaseWith(b -> b.data(ImmutableMap.of())), false, noReferenceSupplied},
            {"null case reference", caseWithAttachReference(null), false, noReferenceSupplied},
            {"invalid type List", caseWithAttachReference(ImmutableList.of()), false, "Invalid case reference: '[]'"},
            {"invalid type Integer", caseWithAttachReference(5), false, "Invalid case reference: '5'"},
            {"valid search case reference", caseWithTargetReference(null, "1234 234 393"), true, "1234234393"},
            {"both search case reference and attach to case reference are null", caseWithTargetReference(null, null), false, "No case reference supplied"},
            {"valid attach to case reference", caseWithTargetReference("1234 234 393", null), true, "1234234393"},
            {"invalid search case ref and attach to case ref null", caseWithTargetReference(null, 56), false, "Invalid case reference: '56'"},
            {"ignore attach to case ref when search case ref exists", caseWithTargetReference(56, "1234234393"), true, "1234234393"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("attachToCaseReferenceTestParams")
    @DisplayName("Should accept and remove non 0-9 chars in the case reference")
    void attachToCaseReferenceTest(String caseReason, CaseDetails input, boolean valid, String realValue) {
        checkValidation(input, valid, realValue, CallbackValidations::hasTargetCaseReference, realValue);
    }

    private static Object[][] searchCaseReferenceTestParams() {
        String noReferenceSupplied = "No case reference supplied";
        String noValidReferenceType = "Cannot validate case reference due to the lack of valid case reference type";
        return new Object[][]{
            {"null case details", null, false, noValidReferenceType},
            {"null data", createCaseWith(b -> b.data(null)), false, noValidReferenceType},
            {"empty data", createCaseWith(b -> b.data(ImmutableMap.of())), false, noValidReferenceType},
            {"no search case reference type", createCaseWith(b -> b.data(ImmutableMap.of("searchCaseReference", "12345"))), false, noValidReferenceType},
            {"invalid search case reference type", caseWithSearchCaseRefTypeAndCaseRef("invalid-type", "12345"), false, noValidReferenceType},

            // test cases for case reference type == ccdCaseReference
            {"CCD ref: generic non number removal", caseWithCcdSearchCaseReference("£1234234393"), true, "1234234393"},
            {"CCD ref: - removal", caseWithCcdSearchCaseReference("1234-234-393"), true, "1234234393"},
            {"CCD ref: space removal", caseWithCcdSearchCaseReference("1234 234 393"), true, "1234234393"},
            {"CCD ref: prefix and post fix spaces removal", caseWithCcdSearchCaseReference("  AH 234 393 "), true, "234393"},
            {"CCD ref: No numbers supplied", caseWithCcdSearchCaseReference("#"), false, "Invalid case reference: '#'"},
            {"CCD ref: empty string", caseWithCcdSearchCaseReference(""), false, "Invalid case reference: ''"},
            {"CCD ref: null case reference", caseWithCcdSearchCaseReference(null), false, noReferenceSupplied},
            {"CCD ref: invalid type List", caseWithCcdSearchCaseReference(ImmutableList.of()), false, "Invalid case reference: '[]'"},
            {"CCD ref: invalid type Integer", caseWithCcdSearchCaseReference(5), false, "Invalid case reference: '5'"},

            // test cases for case reference type == externalCaseReference
            {"External ref: non-empty string", caseWithExternalSearchCaseReference("23424"), true, "23424"},
            {"External ref: empty string", caseWithExternalSearchCaseReference(""), false, "Invalid external case reference: ''"},
            {"External ref: prefix and post fix spaces removal", caseWithExternalSearchCaseReference(" AH 234 393 "), true, "AH 234 393"},
            {"External ref: null case reference", caseWithExternalSearchCaseReference(null), false, noReferenceSupplied},
            {"External ref: invalid type List", caseWithExternalSearchCaseReference(ImmutableList.of()), false, "Invalid external case reference: '[]'"},
            {"External ref: invalid type Integer", caseWithExternalSearchCaseReference(5), false, "Invalid external case reference: '5'"},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("searchCaseReferenceTestParams")
    @DisplayName("Should accept valid case reference")
    void searchCaseReferenceTest(
        String caseDescription,
        CaseDetails inputCase,
        boolean valid,
        String expectedValueOrError
    ) {
        checkValidation(
            inputCase,
            valid,
            expectedValueOrError,
            CallbackValidations::hasSearchCaseReference,
            expectedValueOrError
        );
    }

    private static Object[][] searchCaseReferenceTypeTestParams() {
        return new Object[][]{
            {"null case details", null, false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
            {"null data", createCaseWith(b -> b.data(null)), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
            {"data not containing reference type", createCaseWith(b -> b.data(ImmutableMap.of())), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
            {"invalid search case reference type", caseWithSearchCaseReferenceType("invalid-type"), false, "Invalid case reference type supplied: invalid-type"},
            {"null search case reference type", caseWithSearchCaseReferenceType(null), false, NO_CASE_REFERENCE_TYPE_SUPPLIED_ERROR},
            {"non-string search case reference type", caseWithSearchCaseReferenceType(321), false, "Invalid case reference type supplied: 321"},
            {"CCD case reference type", caseWithSearchCaseReferenceType("ccdCaseReference"), true, "ccdCaseReference"},
            {"External case reference type", caseWithSearchCaseReferenceType("externalCaseReference"), true, "externalCaseReference"}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error/value:{3}")
    @MethodSource("searchCaseReferenceTypeTestParams")
    @DisplayName("Should accept valid case reference type")
    void searchCaseReferenceTypeTest(
        String caseDescription,
        CaseDetails inputCase,
        boolean valid,
        String expectedValueOrError
    ) {
        checkValidation(
            inputCase,
            valid,
            expectedValueOrError,
            CallbackValidations::hasSearchCaseReferenceType,
            expectedValueOrError
        );
    }

    private static Object[][] scannedRecordTestParams() {
        String noDocumentError = "There were no documents in exception record";
        CaseDetails validDoc = caseWithDocument(document("https://url", "fileName.pdf"));
        return new Object[][]{
            {"Correct map with document", validDoc, true, document("https://url", "fileName.pdf"), null},
            {"Null case details", null, false, null, noDocumentError},
            {"Null data supplied", createCaseWith(b -> b.data(null)), false, null, noDocumentError},
            {"Empty data supplied", createCaseWith(b -> b.data(ImmutableMap.of())), false, null, noDocumentError},
            {"Null case document list", caseWithDocument(null), false, null, noDocumentError},
            {"No items in document list", caseWithDocument(emptyList()), false, null, noDocumentError},
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2} error:{4}")
    @MethodSource("scannedRecordTestParams")
    @DisplayName("Should check that at least one scanned record exists")
    void scannedRecordTest(String caseReason,
                           CaseDetails input,
                           boolean valid,
                           List<Map<String, Object>> realValue,
                           String errorString) {
        checkValidation(input, valid, realValue, CallbackValidations::hasAScannedRecord, errorString);
    }

    private static Object[][] caseTypeIdTestParams() {
        return new Object[][]{
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
            CallbackValidations::hasServiceNameInCaseTypeId,
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

    private static Object[][] classificationForAttachToCaseTestParams() {
        return new Object[][]{
            {"Invalid journey classification", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, "invalid_classification"))), false, null, "Journey Classification invalid_classification is not allowed when attaching exception record to a case"},
            {"Invalid journey classification", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, NEW_APPLICATION.name()))), false, null, "The current Journey Classification NEW_APPLICATION is not allowed for attaching to case"},
            {"Valid journey classification(supplementary evidence)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE.name()))), true, SUPPLEMENTARY_EVIDENCE, null},
            {"Valid journey classification(supplementary evidence with ocr)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, SUPPLEMENTARY_EVIDENCE_WITH_OCR.name(), OCR_DATA, asList(ImmutableMap.of("f1", "v1"))))), true, SUPPLEMENTARY_EVIDENCE_WITH_OCR, null},
            {"Valid journey classification(exception)", createCaseWith(b -> b.data(ImmutableMap.of(JOURNEY_CLASSIFICATION, CLASSIFICATION_EXCEPTION))), true, EXCEPTION, null}
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
            CallbackValidations::canBeAttachedToCase,
            expectedValueOrError
        );
    }

    @Test
    void invalidJurisdictionTest() {
        checkValidation(
            createCaseWith(b -> b.jurisdiction(null)),
            false,
            null,
            CallbackValidations::hasJurisdiction,
            "Internal Error: invalid jurisdiction supplied: null"
        );
    }

    @Test
    void noFormTypeTest() {
        checkValidation(
            createCaseWith(b -> b.data(null)),
            false,
            null,
            CallbackValidations::hasFormType,
            "Missing Form Type"
        );
    }

    @Test
    void noCaseIdTest() {
        checkValidation(
            createCaseWith(b -> b.id(null)),
            false,
            null,
            CallbackValidations::hasAnId,
            "Exception case has no Id"
        );
    }

    @Test
    void valid_case_id_should_pass() {
        checkValidation(
            createCaseWith(b -> b.id(1L)),
            true,
            1L,
            CallbackValidations::hasAnId,
            null
        );
    }

    @Test
    void no_case_details_should_fail() {
        checkValidation(
            null,
            false,
            1L,
            CallbackValidations::hasAnId,
            "Exception case has no Id"
        );
    }

    @ParameterizedTest(name = "{0}: valid:{2} value:{3} error:{4}")
    @MethodSource("classificationForAttachToCaseTestParams")
    @DisplayName("Should accept valid journey classification")
    void canJourneyClassificationBeAttachedToCaseTest(
        String caseDescription,
        CaseDetails inputCase,
        boolean valid,
        Classification expectedValue,
        String error
    ) {
        checkValidation(
            inputCase,
            valid,
            expectedValue,
            CallbackValidations::hasJourneyClassificationForAttachToCase,
            error
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
            CallbackValidations::hasIdamToken,
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
            CallbackValidations::hasUserId,
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
            (caseDetails -> CallbackValidations.validatePayments(caseDetails, classification, configItem)),
            expectedValueOrError
        );
    }

    private <T> void checkValidation(CaseDetails input,
                                     boolean valid,
                                     T realValue,
                                     Function<CaseDetails, Validation<String, ?>> validationMethod,
                                     String errorString) {
        Validation<String, ?> validation = validationMethod.apply(input);

        softAssertions(valid, realValue, errorString, validation);
    }

    private <T> void checkValidation(String input,
                                     boolean valid,
                                     T realValue,
                                     Function<String, Validation<String, ?>> validationMethod,
                                     String errorString) {
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
