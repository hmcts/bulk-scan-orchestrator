package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_CREATE_NEW_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("checkstyle:LineLength")
class CreateCaseCallbackServiceTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";
    private static final CreateCaseValidator VALIDATOR = new CreateCaseValidator();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    private CreateCaseCallbackService service;

    @BeforeEach
    void setUp() {
        service = new CreateCaseCallbackService(
            VALIDATOR,
            serviceConfigProvider,
            transformationClient,
            s2sTokenGenerator,
            paymentsProcessor,
            coreCaseDataApi,
            ccdApi
        );
    }

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        CallbackException callbackException = catchThrowableOfType(() ->
            service.process(new CcdCallbackRequest(
                "some event",
                null,
                true
            ), IDAM_TOKEN, USER_ID),
            CallbackException.class
        );

        assertThat(callbackException.getCause()).isNull();
        assertThat(callbackException).hasMessage("The some event event is not supported. Please contact service team");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_missing() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.id(1L));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
            service.process(new CcdCallbackRequest(
                EVENT_ID_CREATE_NEW_CASE,
                caseDetails,
                true
            ), IDAM_TOKEN, USER_ID),
            CallbackException.class
        );

        assertThat(callbackException.getCause()).isNull();
        assertThat(callbackException).hasMessage("No case type ID supplied");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_when_case_type_id_is_empty() {
        // given
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(""));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
            service.process(new CcdCallbackRequest(
                EVENT_ID_CREATE_NEW_CASE,
                caseDetails,
                true
            ), IDAM_TOKEN, USER_ID),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isNull();
        assertThat(callbackException).hasMessage("Case type ID () has invalid format");
        verify(serviceConfigProvider, never()).getConfig(anyString());
    }

    @Test
    void should_not_allow_to_process_callback_in_case_service_not_configured() {
        // given
        doThrow(new ServiceNotConfiguredException("oh no")).when(serviceConfigProvider).getConfig(SERVICE);
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(CASE_TYPE_ID));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
            service.process(new CcdCallbackRequest(
                EVENT_ID_CREATE_NEW_CASE,
                caseDetails,
                true
            ), IDAM_TOKEN, USER_ID),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isNull();
        assertThat(callbackException).hasMessage("oh no");
    }

    @Test
    void should_not_allow_to_process_callback_in_case_transformation_url_not_configured() {
        // given
        when(serviceConfigProvider.getConfig(SERVICE)).thenReturn(new ServiceConfigItem());
        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder.caseTypeId(CASE_TYPE_ID));

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
            service.process(new CcdCallbackRequest(
                EVENT_ID_CREATE_NEW_CASE,
                caseDetails,
                true
            ), IDAM_TOKEN, USER_ID),
            CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isNull();
        assertThat(callbackException).hasMessage("Transformation URL is not configured");
    }

    @Test
    void should_report_error_if_classification_new_application_with_documents_and_without_ocr_data() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", NEW_APPLICATION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails(data),
            true
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).containsOnly(
            String.format(
                "Event %s not allowed for the current journey classification %s without OCR",
                EVENT_ID_CREATE_NEW_CASE,
                NEW_APPLICATION.name()
            )
        );
    }

    @Test
    void should_report_error_if_classification_is_supplementary_evidence() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = basicCaseData();
        data.put("journeyClassification", SUPPLEMENTARY_EVIDENCE.name());

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails(data),
            true
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).containsOnly(
            String.format(
                "Event %s not allowed for the current journey classification %s",
                EVENT_ID_CREATE_NEW_CASE,
                SUPPLEMENTARY_EVIDENCE.name()
            )
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

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails(basicCaseData()),
            true
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.getExceptionRecordData()).isEmpty();
        assertThat(result.getWarnings()).containsOnly("warning");
        assertThat(result.getErrors()).containsOnly("error");
    }

    @Test
    void should_return_service_case_when_it_already_exists_in_ccd_for_a_given_exception_record() throws Exception {
        // given
        setUpTransformationUrl();

        when(ccdApi.getCaseRefsByBulkScanCaseReference(Long.toString(CASE_ID), null))
            .thenReturn(singletonList(345L));

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails(basicCaseData()),
            true
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.getExceptionRecordData().get(CASE_REFERENCE)).isEqualTo("345");
        assertThat(result.getWarnings().isEmpty()).isTrue();
        assertThat(result.getErrors().isEmpty()).isTrue();
    }

    @Test
    void should_return_error_if_multiple_cases_exist_in_ccd_for_a_given_exception_record() throws Exception {
        // given
        setUpTransformationUrl();

        when(ccdApi.getCaseRefsByBulkScanCaseReference(Long.toString(CASE_ID), null))
            .thenReturn(asList(345L, 456L));

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails(basicCaseData()),
            true
        ), IDAM_TOKEN, USER_ID);

        // then

        // then
        assertThat(result.getExceptionRecordData()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).containsOnly(
            "Multiple cases (345, 456) found for the given bulk scan case reference: 123"
        );
    }

    @Test
    void should_warn_about_missing_classification() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = basicCaseData();
        data.remove("journeyClassification");

        CaseDetails caseDetails = caseDetails(data);

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails,
            true
        ), IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).containsOnly("Missing journeyClassification");
    }

    @Test
    void should_report_errors_when_journey_classification_is_invalid() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = basicCaseData();
        data.put("journeyClassification", "EXCEPTIONS");

        CaseDetails caseDetails = caseDetails(data);

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails,
            true
        ), IDAM_TOKEN, USER_ID);

        assertThat(result.getErrors()).containsOnly(
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
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", ImmutableList.of(ImmutableMap.of("value", doc)));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(CASE_ID)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails,
            true
        ), IDAM_TOKEN, USER_ID);

        assertThat(result.getErrors()).containsOnly(
            "Invalid scannedDocuments format. Error: No enum constant " + DocumentType.class.getName() + ".OTHERS"
        );
    }

    @Test
    void should_report_errors_when_ocr_data_is_invalid() {
        // given
        setUpTransformationUrl();

        // modify scannedDocs to proof datetime field is bulletproof
        Map<String, Object> doc = new HashMap<>();

        doc.put("type", "Other");
        doc.put("url", ImmutableMap.of(
            "document_url", "https://some-url",
            "document_binary_url", "https://some-bin-url",
            "document_filename", "some-name"
        ));
        doc.put("controlNumber", "1234");
        doc.put("fileName", "file");
        doc.put("scannedDate", "2019-09-06T15:40:00Z");
        doc.put("deliveryDate", "2019-09-06T15:40:00");

        Map<String, Object> data = new HashMap<>();

        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", ImmutableList.of(ImmutableMap.of("value", doc)));
        data.put("scanOCRData", ImmutableList.of(ImmutableMap.of("value", ImmutableMap.of(
            "key", "k",
            "value", 1
        ))));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(CASE_ID)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails,
            true
        ), IDAM_TOKEN, USER_ID);

        String match =
            "Invalid OCR data format. Error: (class )?java.lang.Integer cannot be cast to (class )?java.lang.String.*";
        assertThat(result.getErrors())
            .hasSize(1)
            .element(0)
            .asString()
            .matches(match);
    }

    @Test
    void should_report_errors_when_form_type_is_null() {
        // given
        setUpTransformationUrl();

        Map<String, Object> data = basicCaseData();
        data.put("formType", null);

        CaseDetails caseDetails = caseDetails(data);

        // when
        ProcessResult result = service.process(new CcdCallbackRequest(
            EVENT_ID_CREATE_NEW_CASE,
            caseDetails,
            true
        ), IDAM_TOKEN, USER_ID);

        String match = "Missing Form Type";
        assertThat(result.getErrors())
            .hasSize(1)
            .element(0)
            .asString()
            .matches(match);
    }

    @Test
    void should_call_payments_processor_when_case_has_payments() throws Exception {
        // given
        setUpTransformationUrl();

        given(transformationClient.transformExceptionRecord(any(), any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        emptyMap()
                    ),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(startCcdEventResp);

        Long newCaseId = 123L;
        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(newCaseId).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

        Map<String, Object> data = basicCaseData();
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES); // has payments!

        CaseDetails caseDetails = caseDetails(data);

        // when
        ProcessResult result =
            service
                .process(
                    new CcdCallbackRequest(EVENT_ID_CREATE_NEW_CASE, caseDetails, true),
                    IDAM_TOKEN,
                    USER_ID
                );

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();

        verify(paymentsProcessor).updatePayments(caseDetails, CASE_ID);
    }

    @Test
    void should_call_payments_processor_when_case_has_no_payments() throws Exception {
        // given
        setUpTransformationUrl();

        given(transformationClient.transformExceptionRecord(any(), any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        emptyMap()
                    ),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(startCcdEventResp);

        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(123L).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

        Map<String, Object> data = basicCaseData();
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.NO); // no payments!

        CaseDetails caseDetails = caseDetails(data);

        // when
        ProcessResult result =
            service
                .process(
                    new CcdCallbackRequest(EVENT_ID_CREATE_NEW_CASE, caseDetails, true),
                    IDAM_TOKEN,
                    USER_ID
                );

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();

        verify(paymentsProcessor).updatePayments(caseDetails, CASE_ID);
    }

    @Test
    void should_return_error_if_publishing_payment_command_failed() throws Exception {
        // given
        setUpTransformationUrl();

        given(transformationClient.transformExceptionRecord(any(), any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails("some_case_type", "some_event_id", emptyMap()),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(startCcdEventResp);

        Long newCaseId = 123L;
        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(newCaseId).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

        CaseDetails caseDetails = caseDetails(basicCaseData());

        PaymentsPublishingException paymentException = new PaymentsPublishingException("Error message", null);
        doThrow(paymentException)
            .when(paymentsProcessor)
            .updatePayments(any(), anyLong());

        // when
        CallbackException exception = catchThrowableOfType(() ->
            service
                .process(
                    new CcdCallbackRequest(EVENT_ID_CREATE_NEW_CASE, caseDetails, true),
                    IDAM_TOKEN,
                    USER_ID
                ),
            CallbackException.class
        );

        // then
        assertThat(exception.getMessage()).isEqualTo("Payment references cannot be processed. Please try again later");
        assertThat(exception.getCause()).isInstanceOf(PaymentsPublishingException.class);
    }

    @ParameterizedTest(name = "Allowed to proceed: {0}. User ignores warnings: {1}")
    @CsvSource({
        "false, false",
        "false, true",
        "true, false",
        // {true, true} case tested separately - no warnings or errors are returned
    })
    void should_return_either_a_warning_or_error_when_payment_are_not_processed_based_on_service_config(
        boolean isAllowedToProceed,
        boolean ignoresWarnings
    ) {
        // given
        setUpServiceConfig("https://localhost", isAllowedToProceed);

        Map<String, Object> data = basicCaseData();
        data.put(ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING, YesNoFieldValues.YES);

        // when
        ProcessResult result =
            service
                .process(
                    new CcdCallbackRequest(EVENT_ID_CREATE_NEW_CASE, caseDetails(data), ignoresWarnings),
                    IDAM_TOKEN,
                    USER_ID
                );

        // then
        if (isAllowedToProceed) {
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getWarnings()).containsExactly(CreateCaseCallbackService.AWAITING_PAYMENTS_MESSAGE);
        } else {
            assertThat(result.getErrors()).containsExactly(CreateCaseCallbackService.AWAITING_PAYMENTS_MESSAGE);
            assertThat(result.getWarnings()).isEmpty();
        }
    }

    @Test
    void should_allow_creating_case_when_payments_are_not_present_but_user_is_allowed_to_proceed_and_ignores_warnings() throws Exception {
        // given
        setUpServiceConfig("https://localhost", true); // allowed to create case despite pending payments

        given(transformationClient.transformExceptionRecord(any(), any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        emptyMap()
                    ),
                    emptyList()
                )
            );

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(mock(StartEventResponse.class));

        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(123L).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

        Map<String, Object> data = basicCaseData();
        data.put(ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING, YesNoFieldValues.YES);

        // when
        ProcessResult result =
            service
                .process(
                    new CcdCallbackRequest(EVENT_ID_CREATE_NEW_CASE, caseDetails(data), true), // ignore warnings
                    IDAM_TOKEN,
                    USER_ID
                );

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    private void setUpTransformationUrl() {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl("url");
        when(serviceConfigProvider.getConfig(SERVICE)).thenReturn(configItem);
    }

    private void setUpServiceConfig(String transformationUrl, boolean allowCreatingCaseBeforePaymentsAreProcessed) {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl(transformationUrl);
        configItem.setAllowCreatingCaseBeforePaymentsAreProcessed(allowCreatingCaseBeforePaymentsAreProcessed);
        when(serviceConfigProvider.getConfig(SERVICE)).thenReturn(configItem);
    }

    private Map<String, Object> basicCaseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, "987");
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, "sample jurisdiction");
        return data;
    }

    private CaseDetails caseDetails(Map<String, Object> data) {
        return TestCaseBuilder.createCaseWith(builder -> builder
            .id(CASE_ID)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );
    }
}
