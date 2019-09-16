package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CreateCaseCallbackServiceTest {

    private static final String EVENT_ID = "createCase";
    private static final String SERVICE = "service";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final CreateCaseValidator VALIDATOR = new CreateCaseValidator();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    private CreateCaseCallbackService service;

    @BeforeEach
    void setUp() {
        service = new CreateCaseCallbackService(
            VALIDATOR,
            serviceConfigProvider
        );
    }

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        Either<List<String>, Map<String, Object>> output = service.process(null, "some event");

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("The some event event is not supported. Please contact service team");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_missing() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.id(1L));

        // when
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("No case type ID supplied");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_empty() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(""));

        // when
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly(
            "Missing poBox",
            "Internal Error: invalid jurisdiction supplied: null",
            "Missing journeyClassification",
            "Missing deliveryDate",
            "Missing openingDate",
            "Missing OCR data"
        );
    }

    @Test
    void should_successfully_create_exception_record_with_documents_and_ocr_data_for_transformation_client() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

        // then
        assertThat(output.isRight()).isTrue();
        assertThat(output.get().keySet()).containsOnly("caseReference");
    }

    @Test
    void should_warn_about_missing_classification() {
        // given
        setUpTransformationUrl();

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(ImmutableMap.of(
                "poBox", "12345",
                "deliveryDate", "2019-09-06T15:30:03.000Z",
                "openingDate", "2019-09-06T15:30:04.000Z",
                "scannedDocuments", TestCaseBuilder.document("https://url", "some doc"),
                "scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value")
            ))
        );

        // when
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

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
        Either<List<String>, Map<String, Object>> output = service.process(caseDetails, EVENT_ID);

        String match =
            "Invalid OCR data format. Error: (class )?java.lang.Integer cannot be cast to (class )?java.lang.String.*";
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
