package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.EnvelopeTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.abortedWithoutFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.caseAlreadyExists;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.caseCreated;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.potentiallyRecoverableFailure;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationResult.unrecoverableFailure;

@ExtendWith(MockitoExtension.class)
class AutoCaseCreatorTest {

    @Mock
    private EnvelopeTransformer envelopeTransformer;
    @Mock
    private CcdApi ccdApi;
    @Mock
    private ServiceConfigProvider serviceConfigProvider;
    @Mock
    private EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper;
    @Mock
    private ServiceConfigItem serviceConfigItem;

    private AutoCaseCreator autoCaseCreator;

    @BeforeEach
    void setUp() {
        given(serviceConfigItem.getAutoCaseCreationEnabled()).willReturn(true);
        given(serviceConfigProvider.getConfig(any())).willReturn(serviceConfigItem);

        autoCaseCreator = new AutoCaseCreator(
            envelopeTransformer,
            ccdApi,
            serviceConfigProvider,
            envelopeReferenceCollectionHelper
        );
    }

    @Test
    void should_not_create_case_when_service_is_disabled_for_auto_case_creation() {
        given(serviceConfigItem.getAutoCaseCreationEnabled()).willReturn(false);

        var result = autoCaseCreator.createCase(envelope(1));

        assertThat(result).usingRecursiveComparison().isEqualTo(abortedWithoutFailure());
        verifyNoInteractions(envelopeTransformer, ccdApi, envelopeReferenceCollectionHelper);
    }

    @Test
    void should_not_create_case_when_a_case_was_already_created_from_the_envelope() {
        var existingCaseId = 1234L;

        verifyCaseIsNotCreatedWhenCasesAlreadyExist(
            asList(existingCaseId),
            caseAlreadyExists(existingCaseId)
        );
    }

    @Test
    void should_not_create_case_when_multiple_cases_reference_the_envelope() {
        verifyCaseIsNotCreatedWhenCasesAlreadyExist(
            asList(123L, 456L),
            unrecoverableFailure()
        );
    }

    @Test
    void should_create_case_when_transformation_is_successful() {
        // given
        long newCaseId = 1234L;
        given(ccdApi.createCase(any(), any(), any(), any(), any())).willReturn(newCaseId);

        var transformationResponse = sampleSuccessfulTransformationResponse();
        given(envelopeTransformer.transformEnvelope(any())).willReturn(right(transformationResponse));

        Envelope envelope = envelope(1);

        // when
        var result = autoCaseCreator.createCase(envelope);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(caseCreated(newCaseId));

        verify(ccdApi).getCaseRefsByEnvelopeId(envelope.id, envelope.container);

        verify(ccdApi).createCase(
            eq(envelope.jurisdiction),
            eq(transformationResponse.caseCreationDetails.caseTypeId),
            eq(transformationResponse.caseCreationDetails.eventId),
            any(), //verified in a separate test
            eq(getExpectedLogContext(envelope))
        );
    }

    @Test
    void should_not_create_case_when_transformation_fails() {
        verifyCaseIsNotCreatedWhenTransformationFails(
            EnvelopeTransformer.TransformationFailureType.POTENTIALLY_RECOVERABLE,
            potentiallyRecoverableFailure()
        );

        verifyCaseIsNotCreatedWhenTransformationFails(
            EnvelopeTransformer.TransformationFailureType.UNRECOVERABLE,
            unrecoverableFailure()
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_pass_a_valid_case_content_creator_to_ccd_api_when_creating_case() {
        // given
        var transformationResponse = sampleSuccessfulTransformationResponse();
        given(envelopeTransformer.transformEnvelope(any())).willReturn(right(transformationResponse));

        var expectedBulkScanEnvelopeReferences =
            asList(new CcdCollectionElement<>(new EnvelopeReference("envRefId1", CaseAction.CREATE)));

        given(envelopeReferenceCollectionHelper.singleEnvelopeReferenceList(any(), any()))
            .willReturn(expectedBulkScanEnvelopeReferences);

        Envelope envelope = envelope(1);

        // run case creation, so that the content builder is created and captured by this test
        autoCaseCreator.createCase(envelope);

        ArgumentCaptor<Function<StartEventResponse, CaseDataContent>> caseDataContentBuilderCaptor =
            ArgumentCaptor.forClass(Function.class);

        verify(ccdApi).createCase(any(), any(), any(), caseDataContentBuilderCaptor.capture(), any());

        verifyCaseDataContentBuilderProducesCorrectResult(
            caseDataContentBuilderCaptor.getValue(),
            envelope,
            transformationResponse,
            expectedBulkScanEnvelopeReferences
        );
    }

    @Test
    void should_return_failure_when_case_creation_results_in_bad_request_response() {
        verifyResultForCcdCaseCreationException(
            ccdErrorResponseException(HttpStatus.BAD_REQUEST),
            unrecoverableFailure()
        );
    }

    @Test
    void should_return_failure_when_case_creation_results_in_unprocessable_entity_response() {
        verifyResultForCcdCaseCreationException(
            ccdErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY),
            unrecoverableFailure()
        );
    }

    @Test
    void should_return_failure_when_case_creation_results_in_other_error_response() {
        verifyResultForCcdCaseCreationException(
            ccdErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR),
            potentiallyRecoverableFailure()
        );
    }

    @Test
    void should_return_failure_when_case_creation_throws_other_exception() {
        verifyResultForCcdCaseCreationException(
            new RuntimeException("test"),
            potentiallyRecoverableFailure()
        );
    }

    private void verifyCaseIsNotCreatedWhenCasesAlreadyExist(
        List<Long> existingCaseIds,
        CaseCreationResult expectedResult
    ) {
        // given
        given(ccdApi.getCaseRefsByEnvelopeId(any(), any())).willReturn(existingCaseIds);

        Envelope envelope = envelope(1);

        // when
        var result = autoCaseCreator.createCase(envelope);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);

        verify(ccdApi).getCaseRefsByEnvelopeId(envelope.id, envelope.container);
        verify(ccdApi, never()).createCase(any(), any(), any(), any(), any());
    }

    private void verifyCaseIsNotCreatedWhenTransformationFails(
        EnvelopeTransformer.TransformationFailureType transformationFailureType,
        CaseCreationResult expectedResult
    ) {
        // given
        given(envelopeTransformer.transformEnvelope(any())).willReturn(left(transformationFailureType));

        Envelope envelope = envelope(1);

        // when
        var result = autoCaseCreator.createCase(envelope);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);

        verify(envelopeTransformer).transformEnvelope(envelope);
        verify(ccdApi, never()).createCase(any(), any(), any(), any(), any());
    }

    private void verifyResultForCcdCaseCreationException(
        Exception ccdCallException,
        CaseCreationResult expectedResult
    ) {
        // given
        willThrow(ccdCallException).given(ccdApi).createCase(any(), any(), any(), any(), any());

        given(envelopeTransformer.transformEnvelope(any()))
            .willReturn(right(sampleSuccessfulTransformationResponse()));

        // when
        var result = autoCaseCreator.createCase(envelope(1));

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);

        verify(ccdApi).createCase(any(), any(), any(), any(), any());
    }

    private void verifyCaseDataContentBuilderProducesCorrectResult(
        Function<StartEventResponse, CaseDataContent> caseDataContentBuilder,
        Envelope inputEnvelope,
        SuccessfulTransformationResponse transformationResponse,
        List<CcdCollectionElement<EnvelopeReference>> receivedBulkScanEnvelopeReferences
    ) {
        // given
        StartEventResponse startEventResponse = sampleStartEventResponse();

        // when
        var caseDataContent = caseDataContentBuilder.apply(startEventResponse);

        // then
        assertThat(caseDataContent).isNotNull();
        assertThat(caseDataContent.getEventToken()).isEqualTo(startEventResponse.getToken());

        Event event = caseDataContent.getEvent();
        assertThat(event).isNotNull();
        assertThat(event.getId()).isEqualTo(startEventResponse.getEventId());
        assertThat(event.getSummary()).isEqualTo("Case created");
        assertThat(event.getDescription()).isEqualTo("Case created from envelope " + inputEnvelope.id);

        assertThat(caseDataContent.getData()).isEqualTo(
            getExpectedCaseDataInCaseCreationEvent(transformationResponse, receivedBulkScanEnvelopeReferences)
        );
    }

    private FeignException ccdErrorResponseException(HttpStatus status) {
        return FeignException.errorStatus(
            "test",
            Response
                .builder()
                .status(status.value())
                .body("test".getBytes(Charset.defaultCharset()))
                .request(Request.create(Request.HttpMethod.POST, "url", emptyMap(), new byte[]{}, null, null))
                .build()
        );
    }

    private Map<String, Object> getExpectedCaseDataInCaseCreationEvent(
        SuccessfulTransformationResponse transformationResponse,
        List<CcdCollectionElement<EnvelopeReference>> bulkScanEnvelopeReferences
    ) {
        Map<String, Object> expectedCaseData = new HashMap<>();
        expectedCaseData.putAll(transformationResponse.caseCreationDetails.caseData);
        expectedCaseData.put("bulkScanEnvelopes", bulkScanEnvelopeReferences);
        return expectedCaseData;
    }

    private StartEventResponse sampleStartEventResponse() {
        return StartEventResponse
            .builder()
            .eventId("startEventResponseEventId1")
            .token("startEventResponseEventToken1")
            .caseDetails(CaseDetails.builder().build())
            .build();
    }

    private SuccessfulTransformationResponse sampleSuccessfulTransformationResponse() {
        var caseCreationDetails = new CaseCreationDetails(
            "caseTypeId1",
            "eventId1",
            ImmutableMap.of("key1", "value1")
        );

        return new SuccessfulTransformationResponse(caseCreationDetails, emptyList());
    }

    private String getExpectedLogContext(Envelope envelope) {
        return format(
            "Envelope ID: %s. File name: %s. Service: %s.",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
    }
}
