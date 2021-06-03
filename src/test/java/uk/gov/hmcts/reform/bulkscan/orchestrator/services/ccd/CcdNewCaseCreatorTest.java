package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.ExceptionRecordTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.validation.ConstraintViolationException;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CcdNewCaseCreatorTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Mock
    private ExceptionRecordTransformer exceptionRecordTransformer;

    @Mock
    private ServiceResponseParser serviceResponseParser;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private CcdApi ccdApi;

    @Mock
    private EnvelopeReferenceHelper envelopeReferenceHelper;

    private CcdNewCaseCreator ccdNewCaseCreator;

    @Mock
    private HttpClientErrorException.UnprocessableEntity unprocessableEntity;

    @Mock
    private HttpClientErrorException.BadRequest badRequest;

    @BeforeEach
    void setUp() {
        ccdNewCaseCreator = new CcdNewCaseCreator(
            exceptionRecordTransformer,
            serviceResponseParser,
            s2sTokenGenerator,
            ccdApi,
            envelopeReferenceHelper
        );
    }

    @Test
    void should_return_new_case_id_when_successfully_executed_all_the_steps() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        var s2sToken = "s2s-token1";

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
            new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
            emptyList()
        );

        given(s2sTokenGenerator.generate()).willReturn(s2sToken);
        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
            .willReturn(expectedTransformationResponse);

        given(ccdApi.createCase(
                any(CcdRequestCredentials.class),
                eq(exceptionRecord.poBoxJurisdiction),
                eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
                eq(expectedTransformationResponse.caseCreationDetails.eventId),
                any(),
                anyString()
        )).willReturn(CASE_ID);

        // when
        CreateCaseResult result =
            ccdNewCaseCreator.createNewCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.caseId).isEqualTo(CASE_ID);

        // and
        var expectedLogContext = format(
            "Exception ID: %s, jurisdiction: %s, form type: %s",
            exceptionRecord.id,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.formType
        );

        var ccdRequestCredentials = ArgumentCaptor.forClass(CcdRequestCredentials.class);
        verify(ccdApi).createCase(
            ccdRequestCredentials.capture(),
            eq(exceptionRecord.poBoxJurisdiction),
            eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
            eq(expectedTransformationResponse.caseCreationDetails.eventId),
            any(), // there's a separate test for this argument (case data content builder)
            eq(expectedLogContext)
        );
        assertThat(ccdRequestCredentials.getValue().idamToken).isEqualTo(IDAM_TOKEN);
        assertThat(ccdRequestCredentials.getValue().s2sToken).isEqualTo(s2sToken);
        assertThat(ccdRequestCredentials.getValue().userId).isEqualTo(USER_ID);
    }

    @Test
    void should_return_new_case_id_if_transformation_returns_warnings_and_ignore_warnings_true() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        var s2sToken = "s2s-token1";

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
            new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
            singletonList("warning1")
        );

        given(s2sTokenGenerator.generate()).willReturn(s2sToken);
        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
            .willReturn(expectedTransformationResponse);

        given(ccdApi.createCase(
                any(CcdRequestCredentials.class),
                eq(exceptionRecord.poBoxJurisdiction),
                eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
                eq(expectedTransformationResponse.caseCreationDetails.eventId),
                any(),
                anyString()
        )).willReturn(CASE_ID);

        // when
        CreateCaseResult result =
            ccdNewCaseCreator.createNewCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.caseId).isEqualTo(CASE_ID);

        // and
        var expectedLogContext = format(
            "Exception ID: %s, jurisdiction: %s, form type: %s",
            exceptionRecord.id,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.formType
        );

        var ccdRequestCredentials = ArgumentCaptor.forClass(CcdRequestCredentials.class);
        verify(ccdApi).createCase(
            ccdRequestCredentials.capture(),
            eq(exceptionRecord.poBoxJurisdiction),
            eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
            eq(expectedTransformationResponse.caseCreationDetails.eventId),
            any(), // there's a separate test for this argument (case data content builder)
            eq(expectedLogContext)
        );
        assertThat(ccdRequestCredentials.getValue().idamToken).isEqualTo(IDAM_TOKEN);
        assertThat(ccdRequestCredentials.getValue().s2sToken).isEqualTo(s2sToken);
        assertThat(ccdRequestCredentials.getValue().userId).isEqualTo(USER_ID);
    }

    @Test
    void should_return_warnings_if_transformation_returns_warnings_and_ignore_warnings_false() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
            new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
            singletonList("warning1")
        );

        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
            .willReturn(expectedTransformationResponse);

        // when
        CreateCaseResult result =
            ccdNewCaseCreator.createNewCase(exceptionRecord, configItem, false, IDAM_TOKEN, USER_ID);

        // then
        assertThat(result.caseId).isNull();
        assertThat(result.warnings).containsExactly("warning1");
        assertThat(result.errors).isEmpty();

        verifyNoInteractions(ccdApi);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_provide_correct_case_data_for_ccd(boolean isServiceEnabledForAutoCaseCreation) {
        // given
        var transformationResponse = new SuccessfulTransformationResponse(
            new CaseCreationDetails("case_type_1", "event_id_1", basicCaseData()),
            emptyList()
        );

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        given(s2sTokenGenerator.generate()).willReturn("s2s-token1");
        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
                .willReturn(transformationResponse);

        // when
        // trigger case creation to capture data content builder which was passed to CCD client
        ccdNewCaseCreator.createNewCase(exceptionRecord, configItem, true, IDAM_TOKEN, USER_ID);

        var caseDetailsBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        verify(ccdApi).createCase(
                any(CcdRequestCredentials.class),
                eq(exceptionRecord.poBoxJurisdiction),
                eq(transformationResponse.caseCreationDetails.caseTypeId),
                eq(transformationResponse.caseCreationDetails.eventId),
                caseDetailsBuilderCaptor.capture(),
                anyString()
        );

        // then
        assertCaseDataContentBuilderCreatesCorrectResult(
                caseDetailsBuilderCaptor.getValue(),
                configItem.getService(),
                exceptionRecord.id,
                exceptionRecord.envelopeId,
                transformationResponse,
                isServiceEnabledForAutoCaseCreation
        );
    }

    @Test
    void should_return_errors_and_warnings_when_transformation_client_returns_unprocessable_entity() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        given(serviceResponseParser.parseResponseBody(unprocessableEntity))
            .willReturn(new ClientServiceErrorResponse(singletonList("error"), singletonList("warning")));
        doThrow(unprocessableEntity)
            .when(exceptionRecordTransformer)
            .transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord);


        // when
        CreateCaseResult result = ccdNewCaseCreator.createNewCase(
            exceptionRecord,
            configItem,
            true,
            IDAM_TOKEN,
            USER_ID
        );

        // then
        assertThat(result.warnings).containsOnly("warning");
        assertThat(result.errors).containsOnly("error");

        verifyNoInteractions(ccdApi);
    }

    @Test
    void should_throw_CallbackException_when_transformation_client_returns_bad_request() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        doThrow(badRequest)
            .when(exceptionRecordTransformer)
            .transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord);

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdNewCaseCreator.createNewCase(
                        exceptionRecord,
                        configItem,
                        true,
                        IDAM_TOKEN,
                        USER_ID
                ),
                CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isEqualTo(badRequest);
        assertThat(callbackException).hasMessage("Failed to transform exception record with Id " + exceptionRecord.id);

        verifyNoInteractions(ccdApi);
    }

    @Test
    void should_throw_CallbackException_when_transformation_client_throws_constraint_violation_exception() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        ConstraintViolationException exception = new ConstraintViolationException("msg", emptySet());
        doThrow(exception)
            .when(exceptionRecordTransformer)
            .transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord);

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdNewCaseCreator.createNewCase(
                        exceptionRecord,
                        configItem,
                        true,
                        IDAM_TOKEN,
                        USER_ID
                ),
                CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isEqualTo(exception);
        assertThat(callbackException).hasMessage(
                "Invalid response received from transformation endpoint. "
                        + "Service: " + configItem.getService()
                        + ", exception record: " + exceptionRecord.id
                        + ", violations: " + exception.getMessage()
        );

        verifyNoInteractions(ccdApi);
    }

    @Test
    void should_throw_CallbackException_when_transformation_client_throws_rest_client_exception() {
        // given
        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        RestClientException exception = new RestClientException("msg");
        doThrow(exception)
            .when(exceptionRecordTransformer)
            .transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord);

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdNewCaseCreator.createNewCase(
                        exceptionRecord,
                        configItem,
                        true,
                        IDAM_TOKEN,
                        USER_ID
                ),
                CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isEqualTo(exception);
        assertThat(callbackException).hasMessage(
                "Failed to receive transformed exception record from service " + configItem.getService()
                        + " for exception record " + exceptionRecord.id
        );

        verifyNoInteractions(ccdApi);
    }

    @Test
    void should_throw_CallbackException_when_ccd_client_throws_exception() {
        // given
        var s2sToken = "s2s-token1";

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
                new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
                emptyList()
        );

        given(s2sTokenGenerator.generate()).willReturn(s2sToken);
        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
                .willReturn(expectedTransformationResponse);

        RuntimeException exception = new RuntimeException("msg");
        doThrow(exception)
                .when(ccdApi)
                .createCase(
                        any(CcdRequestCredentials.class),
                        eq(exceptionRecord.poBoxJurisdiction),
                        eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
                        eq(expectedTransformationResponse.caseCreationDetails.eventId),
                        any(),
                        anyString()
            );

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdNewCaseCreator.createNewCase(
                        exceptionRecord,
                        configItem,
                        true,
                        IDAM_TOKEN,
                        USER_ID
                ),
                CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isEqualTo(exception);
        assertThat(callbackException).hasMessage(
                "Failed to create new case for exception record with Id " + exceptionRecord.id
                        + ". Service: " + configItem.getService()
        );
    }

    @Test
    void should_throw_CallbackException_when_ccd_client_throws_feign_exception() {
        // given
        var s2sToken = "s2s-token1";

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
                new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
                emptyList()
        );

        given(s2sTokenGenerator.generate()).willReturn(s2sToken);
        given(exceptionRecordTransformer.transformExceptionRecord(configItem.getTransformationUrl(), exceptionRecord))
                .willReturn(expectedTransformationResponse);

        FeignException exception = new FeignException.InternalServerError(
                "msg",
                Request.create(Request.HttpMethod.POST, "url", emptyMap(), null, null, null),
                "content".getBytes(StandardCharsets.UTF_8)
        );
        doThrow(exception)
                .when(ccdApi)
                .createCase(
                        any(CcdRequestCredentials.class),
                        eq(exceptionRecord.poBoxJurisdiction),
                        eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
                        eq(expectedTransformationResponse.caseCreationDetails.eventId),
                        any(),
                        anyString()
            );

        // when
        CallbackException callbackException = catchThrowableOfType(() ->
                ccdNewCaseCreator.createNewCase(
                        exceptionRecord,
                        configItem,
                        true,
                        IDAM_TOKEN,
                        USER_ID
                ),
                CallbackException.class
        );

        // then
        assertThat(callbackException.getCause()).isEqualTo(exception);
        assertThat(callbackException).hasMessage(
                "Failed to create new case for exception record with Id " + exceptionRecord.id
                        + ". Service: " + configItem.getService()
        );
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            "1",
            CASE_TYPE_ID,
            "envelopeId123",
            "12345",
            "some jurisdiction",
            EXCEPTION,
            "Form1",
            now(),
            now(),
            emptyList(),
            emptyList()
        );
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

    private void assertCaseDataContentBuilderCreatesCorrectResult(
            Function<StartEventResponse, CaseDataContent> caseDetailsBuilder,
            String service,
            String exceptionRecordId,
            String envelopeId,
            SuccessfulTransformationResponse transformationResponse,
            boolean isServiceEnabledForAutoCaseCreation
    ) {
        // given
        var envelopeReferences =
                singletonList(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, CaseAction.CREATE)));

        given(envelopeReferenceHelper.serviceSupportsEnvelopeReferences(service))
            .willReturn(isServiceEnabledForAutoCaseCreation);

        var expectedCaseData = getExpectedCaseDataToPassToCcd(
            transformationResponse,
            exceptionRecordId,
            isServiceEnabledForAutoCaseCreation,
            envelopeReferences
        );

        StartEventResponse startEventResponse = sampleStartEventResponse();

        // when
        var result = caseDetailsBuilder.apply(startEventResponse);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getData()).usingRecursiveComparison().isEqualTo(expectedCaseData);
        assertThat(result.getEventToken()).isEqualTo(startEventResponse.getToken());
        assertThat(result.getEvent().getId()).isEqualTo(startEventResponse.getEventId());
        assertThat(result.getEvent().getSummary()).isEqualTo("Case created");
        assertThat(result.getEvent().getDescription())
            .isEqualTo("Case created from exception record ref " + exceptionRecordId);
    }

    private HashMap<String, Object> getExpectedCaseDataToPassToCcd(
        SuccessfulTransformationResponse transformationResponse,
        String exceptionRecordId,
        boolean isServiceEnabledForAutoCaseCreation,
        List<CcdCollectionElement<EnvelopeReference>> expectedEnvelopeReferences
    ) {
        var expectedCaseData = newHashMap(transformationResponse.caseCreationDetails.caseData);

        if (isServiceEnabledForAutoCaseCreation) {
            expectedCaseData.put("bulkScanEnvelopes", expectedEnvelopeReferences);
        }

        expectedCaseData.put("bulkScanCaseReference", exceptionRecordId);
        return expectedCaseData;
    }

    private ServiceConfigItem getConfigItem() {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl("url");
        return configItem;
    }

    private StartEventResponse sampleStartEventResponse() {
        return StartEventResponse.builder().caseDetails(
            CaseDetails
                .builder()
                .caseTypeId("caseTypeId1")
                .jurisdiction("jurisdiction1")
                .build())
            .eventId("eventId1")
            .token("eventToken1")
            .build();
    }
}
