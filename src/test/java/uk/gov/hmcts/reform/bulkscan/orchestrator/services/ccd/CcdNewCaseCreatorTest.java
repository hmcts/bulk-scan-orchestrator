package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
    private EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper;

    private CcdNewCaseCreator ccdNewCaseCreator;

    @Mock
    private HttpClientErrorException.UnprocessableEntity unprocessableEntity;

    @BeforeEach
    void setUp() {
        ccdNewCaseCreator = new CcdNewCaseCreator(
            exceptionRecordTransformer,
            serviceResponseParser,
            s2sTokenGenerator,
            ccdApi,
            envelopeReferenceCollectionHelper
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_return_new_case_id_when_successfully_executed_all_the_steps() {
        // given
        var s2sToken = "s2s-token1";

        var expectedTransformationResponse = new SuccessfulTransformationResponse(
            new CaseCreationDetails("some_case_type", "some_event_id", basicCaseData()),
            emptyList()
        );

        given(s2sTokenGenerator.generate()).willReturn(s2sToken);
        given(exceptionRecordTransformer.transformExceptionRecord(any(), any()))
            .willReturn(expectedTransformationResponse);

        given(ccdApi.createCase(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()
        )).willReturn(CASE_ID);

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

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

        verify(ccdApi).createCase(
            eq(IDAM_TOKEN),
            eq(s2sToken),
            eq(USER_ID),
            eq(exceptionRecord.poBoxJurisdiction),
            eq(expectedTransformationResponse.caseCreationDetails.caseTypeId),
            eq(expectedTransformationResponse.caseCreationDetails.eventId),
            any(), // there's a separate test for this argument (case data content builder)
            eq(expectedLogContext)
        );
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

        given(s2sTokenGenerator.generate()).willReturn("s2s-token1");
        given(exceptionRecordTransformer.transformExceptionRecord(any(), any())).willReturn(transformationResponse);

        ExceptionRecord exceptionRecord = getExceptionRecord();

        // trigger case creation to capture data content builder which was passed to CCD client
        ccdNewCaseCreator.createNewCase(exceptionRecord, getConfigItem(), true, IDAM_TOKEN, USER_ID);

        var caseDetailsBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        verify(ccdApi).createCase(any(), any(), any(), any(), any(), any(), caseDetailsBuilderCaptor.capture(), any());

        // then
        assertCaseDataContentBuilderCreatesCorrectResult(
            caseDetailsBuilderCaptor.getValue(),
            exceptionRecord.id,
            exceptionRecord.envelopeId,
            transformationResponse,
            isServiceEnabledForAutoCaseCreation
        );
    }

    @Test
    void should_throw_UnprocessableEntityException_when_transformation_client_returns_422() {
        // given
        given(serviceResponseParser.parseResponseBody(unprocessableEntity))
            .willReturn(new ClientServiceErrorResponse(singletonList("error"), singletonList("warning")));
        doThrow(unprocessableEntity)
            .when(exceptionRecordTransformer)
            .transformExceptionRecord(anyString(), any(ExceptionRecord.class));

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

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
        String exceptionRecordId,
        String envelopeId,
        SuccessfulTransformationResponse transformationResponse,
        boolean isServiceEnabledForAutoCaseCreation
    ) {
        // given
        var envelopeReferences =
            asList(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, CaseAction.CREATE)));

        given(envelopeReferenceCollectionHelper.serviceSupportsEnvelopeReferences(any()))
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
