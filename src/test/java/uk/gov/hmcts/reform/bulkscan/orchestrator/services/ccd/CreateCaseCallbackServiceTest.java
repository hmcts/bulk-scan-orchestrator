package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.CaseTransformationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

@ExtendWith(MockitoExtension.class)
class CreateCaseCallbackServiceTest {

    private static final String EVENT_ID = "createCase";
    private static final String SERVICE = "service";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final CreateCaseValidator VALIDATOR = new CreateCaseValidator();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    private CreateCaseCallbackService service;

    @BeforeEach
    void setUp() {
        service = new CreateCaseCallbackService(
            VALIDATOR,
            serviceConfigProvider,
            transformationClient,
            s2sTokenGenerator
        );
    }

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            "some event",
            null,
            true
        ));

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("The some event event is not supported. Please contact service team");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_missing() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.id(1L));

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("No case type ID supplied");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_empty() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(""));

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("Case type ID () has invalid format");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_in_case_service_not_configured() {
        // given
        doThrow(new ServiceNotConfiguredException("oh no")).when(serviceConfigProvider).getConfig(SERVICE);
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(CASE_TYPE_ID));

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("oh no");
    }

    @Test
    void should_not_allow_to_process_callback_in_case_transformation_url_not_configured() {
        // given
        when(serviceConfigProvider.getConfig(SERVICE)).thenReturn(new ServiceConfigItem());
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(CASE_TYPE_ID));

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("Transformation URL is not configured");
    }

    @Test
    void should_report_all_errors_when_null_is_provided_as_case_details() {
        // given
        setUpTransformationUrl();

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(CASE_TYPE_ID));

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Missing poBox",
            "Internal Error: invalid jurisdiction supplied: null",
            "Missing journeyClassification",
            "Missing Form Type",
            "Missing deliveryDate",
            "Missing openingDate"
        );
    }

    // todo happy path will go into integration test once endpoint is created
    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void should_create_exception_record_if_classification_new_application_with_documents_and_ocr_data(
        boolean ignoreWarnings
    ) throws IOException, CaseTransformationException {
        // given
        setUpTransformationUrl();
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        when(transformationClient.transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString()))
            .thenReturn(new SuccessfulTransformationResponse(
                null,
                singletonList("some warning")
            ));

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", NEW_APPLICATION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            ignoreWarnings
        ));

        // then
        if (ignoreWarnings) {
            assertThat(output.isRight()).isTrue();
            assertThat(output.get().getModifiedFields().keySet()).containsOnly("caseReference");
        } else {
            assertThat(output.isLeft()).isTrue();
            assertThat(output.getLeft()).containsOnly("some warning");
        }
    }

    @Test
    void should_create_exception_record_if_classification_new_application_with_documents_and_without_ocr_data()
        throws IOException, CaseTransformationException {
        // given
        setUpTransformationUrl();
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        when(transformationClient.transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString()))
            .thenReturn(new SuccessfulTransformationResponse(
                null,
                emptyList()
            ));

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", NEW_APPLICATION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isRight()).isTrue();
        assertThat(output.get().getModifiedFields().keySet()).containsOnly("caseReference");
        assertThat(output.get().getWarnings()).isEmpty();
    }

    @Test
    void should_create_exception_record_classification_exception_with_documents_and_ocr_data_for_transformation_client()
        throws IOException, CaseTransformationException {
        // given
        setUpTransformationUrl();
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        when(transformationClient.transformExceptionRecord(eq("url"), any(ExceptionRecord.class), anyString()))
            .thenReturn(new SuccessfulTransformationResponse(null, emptyList()));

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isRight()).isTrue();
        assertThat(output.get().getModifiedFields().keySet()).containsOnly("caseReference");
        assertThat(output.get().getWarnings()).isEmpty();

        // and verify all calls were made
        verify(transformationClient).transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString());
    }

    @Test
    void should_report_error_if_classification_is_supplementary_evidence() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", SUPPLEMENTARY_EVIDENCE.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Event createCase not allowed for the current journey classification SUPPLEMENTARY_EVIDENCE"
        );
    }

    @Test
    void should_report_error_if_classification_is_exception_without_ocr() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Event createCase not allowed for the current journey classification EXCEPTION without OCR"
        );
    }

    @Test
    void should_report_with_internal_error_message_when_transformation_client_throws_exception()
        throws IOException, CaseTransformationException {
        // given
        setUpTransformationUrl();
        CaseTransformationException exception = new CaseTransformationException(
            new HttpClientErrorException(HttpStatus.CONFLICT),
            "oh no"
        );
        doThrow(exception)
            .when(transformationClient)
            .transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString());
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft())
            .hasSize(1)
            .containsOnly("Internal error. " + exception.getMessage());
    }

    // todo move to integration test
    @Test
    void should_throw_InvalidCaseDataException_when_transformation_client_returns_422()
        throws IOException, CaseTransformationException {
        // given
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        setUpTransformationUrl();
        InvalidCaseDataException exception = new InvalidCaseDataException(
            new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY),
            new TransformationErrorResponse(singletonList("error"), singletonList("warning"))
        );
        doThrow(exception)
            .when(transformationClient)
            .transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString());

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(1L)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isRight()).isTrue();
        assertThat(output.get().getModifiedFields()).isEmpty();
        assertThat(output.get().getWarnings()).containsOnly("warning");
        assertThat(output.get().getErrors()).containsOnly("error");
    }

    @Test
    void should_warn_about_missing_classification() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("formType", "Form1");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("Missing journeyClassification");
    }

    @Test
    void should_report_errors_when_journey_classification_is_invalid() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTIONS");
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "filename"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        assertThat(output.getLeft()).containsOnly(
            "Invalid journeyClassification. Error: No enum constant " + Classification.class.getName() + ".EXCEPTIONS"
        );
    }

    @Test
    void should_report_errors_when_scanned_document_is_invalid() {
        // given
        setUpTransformationUrl();

        Map<String, Object> doc = new HashMap<>();

        // putting 6 via `ImmutableMap` is available from Java 9
        doc.put("type", "Others");
        doc.put("url", ImmutableMap.of(
            "document_filename", "name"
        ));
        doc.put("controlNumber", "1234");
        doc.put("fileName", "file");
        doc.put("scannedDate", "2019-09-06T15:40:00.000Z");
        doc.put("deliveryDate", "2019-09-06T15:40:00.001Z");

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTION");
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", ImmutableList.of(ImmutableMap.of("value", doc)));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        assertThat(output.getLeft()).containsOnly(
            "Invalid scannedDocuments format. Error: No enum constant " + DocumentType.class.getName() + ".OTHERS"
        );
    }

    @Test
    void should_report_errors_when_ocr_data_is_invalid() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTION");
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", ImmutableList.of(ImmutableMap.of("value", ImmutableMap.of(
            "key", "k",
            "value", 1
        ))));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        String match =
            "Invalid OCR data format. Error: (class )?java.lang.Integer cannot be cast to (class )?java.lang.String.*";
        assertThat(output.getLeft())
            .hasSize(1)
            .element(0)
            .asString()
            .matches(match);
    }

    @Test
    void should_report_errors_when_form_type_is_null() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTION");
        data.put("formType", null);
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            EVENT_ID,
            caseDetails,
            true
        ));

        String match = "Missing Form Type";
        assertThat(output.getLeft())
            .hasSize(1)
            .element(0)
            .asString()
            .matches(match);
    }

    private void setUpTransformationUrl() {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl("url");
        when(serviceConfigProvider.getConfig(SERVICE)).thenReturn(configItem);
    }
}
