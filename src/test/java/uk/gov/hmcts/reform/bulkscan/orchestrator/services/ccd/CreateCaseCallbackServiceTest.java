package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

@ExtendWith(MockitoExtension.class)
class CreateCaseCallbackServiceTest {

    private static final String EVENT_ID = "createNewCase";
    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final CreateCaseValidator VALIDATOR = new CreateCaseValidator();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private CoreCaseDataApi ccdApi;

    private CreateCaseCallbackService service;

    @BeforeEach
    void setUp() {
        service = new CreateCaseCallbackService(
            VALIDATOR,
            serviceConfigProvider,
            transformationClient,
            s2sTokenGenerator,
            ccdApi
        );
    }

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        Either<List<String>, ProcessResult> output = service.process(new CcdCallbackRequest(
            "some event",
            null,
            true
        ), IDAM_TOKEN, USER_ID);

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
        ), IDAM_TOKEN, USER_ID);

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
        ), IDAM_TOKEN, USER_ID);

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
        ), IDAM_TOKEN, USER_ID);

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
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("Transformation URL is not configured");
    }

    @Test
    void should_report_error_if_classification_new_application_with_documents_and_without_ocr_data()
        throws IOException, CaseTransformationException {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", NEW_APPLICATION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Event " + EVENT_ID + " not allowed for the current journey classification NEW_APPLICATION without OCR"
        );
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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Event " + EVENT_ID + " not allowed for the current journey classification SUPPLEMENTARY_EVIDENCE"
        );
    }

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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("formType", "Form1");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
        doc.put("scannedDate", "2019-09-06T15:40:00");
        doc.put("deliveryDate", "2019-09-06T15:40:00.001Z");

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", "EXCEPTION");
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
        data.put("deliveryDate", "2019-09-06T15:30:03");
        data.put("openingDate", "2019-09-06T15:30:04");
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
        ), IDAM_TOKEN, USER_ID);

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
