package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.PO_BOX;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithFormTypeAndClassification;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithScannedDocumentData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.caseWithType;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.TestExceptionRecordCaseBuilder.createValidExceptionRecordCase;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.JOURNEY_CLASSIFICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;

class ExceptionRecordValidatorTest {

    private static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    private static final ExceptionRecordValidator VALIDATOR = new ExceptionRecordValidator();

    @Test
    void should_map_to_exception_record_when_case_details_are_valid() {
        // given
        var validExceptionRecord = createValidExceptionRecordCase();

        // when
        var validation = VALIDATOR.getValidation(validExceptionRecord);

        // then
        assertExceptionRecordMappings(validation);
    }

    @Test
    void should_not_return_errors_when_form_type_is_missing_for_exception_classification() {
        // given
        var caseDetails = caseWithFormTypeAndClassification(
            null,
            EXCEPTION.name()
        );

        // when
        var validation = VALIDATOR.getValidation(caseDetails);

        // then
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.get()).isNotNull();
    }

    @Test
    void should_return_errors_when_validations_fail() {
        // given
        var caseDetails = caseWithId(null);

        // when
        var validation = VALIDATOR.mandatoryPrerequisites(
            () -> VALIDATOR.getCaseId(caseDetails).map(item -> null),
            () -> CallbackValidations.hasCaseTypeId(caseDetails).map(item -> null)
        );

        // then
        assertThat(validation.isInvalid()).isTrue();
        assertThat(validation.getError()).isEqualTo("Exception case has no Id");
    }

    @Test
    void should_not_return_errors_when_validations_are_success() {
        // given
        var caseDetails = createValidExceptionRecordCase();

        // when
        var validation = VALIDATOR.mandatoryPrerequisites(
            () -> VALIDATOR.getCaseId(caseDetails).map(item -> null),
            () -> CallbackValidations.hasCaseTypeId(caseDetails).map(item -> null)
        );

        // then
        assertThat(validation.isInvalid()).isFalse();
        assertThat(validation.get()).isNull();
    }

    @Test
    void should_return_error_when_case_details_contain_invalid_ocr_data() {
        // given
        var invalidOcrData = ImmutableList.of(
            ImmutableMap.of("value", ImmutableMap.of(
                "key", "first_name",
                "value", 1
            )));

        String errorPattern = "Invalid OCR data format. Error: (class )?java.lang.Integer "
            + "cannot be cast to (class )?java.lang.String.*";

        // then
        checkValidationErrorMatches(
            caseWithData("scanOCRData", invalidOcrData),
            VALIDATOR::getValidation,
            errorPattern
        );
    }

    private static Object[][] invalidCaseDataTestParams() {
        return new Object[][]{
            {"Missing case id", caseWithId(null), "Exception case has no Id"},
            {"Missing case type", caseWithType(null), "Missing caseType"},
            {"Missing jurisdiction", caseWithJurisdiction(null), "Internal Error: invalid jurisdiction supplied: null"},
            {"Missing po box", caseWithData("poBox", null), "Missing poBox"},
            {"Missing classification", caseWithData(JOURNEY_CLASSIFICATION, null), "Missing journeyClassification"},
            {"Missing form type", caseWithFormTypeAndClassification(null, NEW_APPLICATION.name()), "Missing Form Type"},
            {"Missing delivery date", caseWithData("deliveryDate", null), "Missing deliveryDate"},
            {"Missing opening date", caseWithData("openingDate", null), "Missing openingDate"},
        };
    }

    @ParameterizedTest(name = "{0}: input:{1} error:{2}")
    @MethodSource("invalidCaseDataTestParams")
    void should_return_errors_for_invalid_case_data(String caseReason, CaseDetails input, String error) {
        // when
        var validation = VALIDATOR.getValidation(input);

        // then
        assertSoftly(softly -> {
            softly.assertThat(validation.isValid()).isFalse();
            List<String> errors = validation.getError().toList();
            softly.assertThat(errors).contains(error);
        });
    }

    private static Object[][] invalidDocumentTestParams() {
        String documentTypeError = "Invalid scannedDocuments format. Error: No enum constant.*DocumentType.*";
        String invalidDateError = "Invalid scannedDocuments format. Error: [Tt]ext.*(could not be parsed)?.*";
        return new Object[][]{
            {"Invalid document type", caseWithScannedDocumentData("type", "invalid-type"), documentTypeError},
            {"Invalid document scanned date", caseWithScannedDocumentData("scannedDate", "date"), invalidDateError},
            {"Invalid document delivery date", caseWithScannedDocumentData("deliveryDate", "date"), invalidDateError},
            {"Invalid scanned date format", caseWithScannedDocumentData("scannedDate", "010101"), invalidDateError},
            {"Invalid delivery date format", caseWithScannedDocumentData(
                "deliveryDate",
                "01-01-122"
            ), invalidDateError},
            {"Null delivery date format", caseWithScannedDocumentData("deliveryDate", null), invalidDateError},
            {"Null scanned date format", caseWithScannedDocumentData("scannedDate", null), invalidDateError},
        };
    }

    @ParameterizedTest(name = "{0}: input:{1} error:{2}")
    @MethodSource("invalidDocumentTestParams")
    void should_return_errors_for_invalid_case_documents(String caseReason, CaseDetails input, String error) {
        checkValidationErrorMatches(
            input,
            VALIDATOR::getValidation,
            error
        );
    }

    @Test
    void should_throw_exception_when_envelopeId_is_missing() {
        // given
        var caseDetails = caseWithData("envelopeId", null);

        // then
        assertThatCode(() -> VALIDATOR.getValidation(caseDetails))
            .isInstanceOf(UnprocessableCaseDataException.class)
            .hasMessage("Exception record is lacking envelopeId field");
    }

    private void checkValidationErrorMatches(
        CaseDetails input,
        Function<CaseDetails, Validation<Seq<String>, ExceptionRecord>> validationMethod,
        String errorMessagePattern
    ) {
        Validation<Seq<String>, ExceptionRecord> validation = validationMethod.apply(input);

        assertSoftly(softly -> {
            softly.assertThat(validation.isValid()).isFalse();
            List<String> errors = validation.getError().toList();
            softly.assertThat(errors.size()).isEqualTo(1);
            softly.assertThat(errors.get(0)).matches(errorMessagePattern);
        });
    }

    private void assertExceptionRecordMappings(Validation<Seq<String>, ExceptionRecord> validation) {
        assertSoftly(softly -> {
            softly.assertThat(validation.isValid()).isTrue();
            ExceptionRecord exceptionRecord = validation.get();
            softly.assertThat(exceptionRecord.id).isEqualTo("1234");
            softly.assertThat(exceptionRecord.caseTypeId).isEqualTo(CASE_TYPE_EXCEPTION_RECORD);
            softly.assertThat(exceptionRecord.envelopeId).isEqualTo("envelopeId123");
            softly.assertThat(exceptionRecord.poBox).isEqualTo(PO_BOX);
            softly.assertThat(exceptionRecord.poBoxJurisdiction).isEqualTo(JURSIDICTION);
            softly.assertThat(exceptionRecord.journeyClassification).isEqualTo(NEW_APPLICATION);
            softly.assertThat(exceptionRecord.formType).isEqualTo("personal");
            softly.assertThat(exceptionRecord.deliveryDate).isBefore(LocalDateTime.now());
            softly.assertThat(exceptionRecord.openingDate).isBefore(LocalDateTime.now());
            softly.assertThat(exceptionRecord.scannedDocuments).isNotEmpty();
            softly.assertThat(exceptionRecord.scannedDocuments.size()).isEqualTo(1);

            // ocr data fields
            softly.assertThat(exceptionRecord.ocrDataFields).isNotEmpty();
            softly.assertThat(exceptionRecord.ocrDataFields.size()).isEqualTo(1);
            OcrDataField ocrDataField = exceptionRecord.ocrDataFields.get(0);
            softly.assertThat(ocrDataField.name).isEqualTo("firstName");
            softly.assertThat(ocrDataField.value).isEqualTo("John");

            // scanned documents
            ScannedDocument document = exceptionRecord.scannedDocuments.get(0);
            softly.assertThat(document.controlNumber).isEqualTo("12341234");
            softly.assertThat(document.type).isEqualTo(DocumentType.FORM);
            softly.assertThat(document.subtype).isEqualTo("personal");
            softly.assertThat(document.fileName).isEqualTo("file1.pdf");
            softly.assertThat(document.documentUrl.url).isEqualTo("http://locahost");
            softly.assertThat(document.documentUrl.binaryUrl).isEqualTo("http://locahost/binary");
            softly.assertThat(document.scannedDate).isBefore(LocalDateTime.now());
            softly.assertThat(document.deliveryDate).isBefore(LocalDateTime.now());
        });
    }
}
