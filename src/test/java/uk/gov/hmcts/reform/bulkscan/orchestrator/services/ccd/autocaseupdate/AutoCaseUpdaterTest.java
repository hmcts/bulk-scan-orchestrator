package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleEnvelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleUpdateDataResponse;

@ExtendWith(MockitoExtension.class)
class AutoCaseUpdaterTest {

    @Mock CaseUpdateDetailsService caseUpdateDataService;
    @Mock CcdApi ccdApi;
    @Mock AutoCaseUpdateCaseDataBuilder caseDataBuilder;

    @Mock CaseDataContent caseDataContent;

    @Captor ArgumentCaptor<Function<StartEventResponse, CaseDataContent>> caseBuilderCaptor;


    AutoCaseUpdater service;

    @BeforeEach
    void setUp() {
        this.service = new AutoCaseUpdater(caseUpdateDataService, ccdApi, caseDataBuilder);
    }

    @Test
    void should_abandon_update_if_case_is_not_found() {
        // given
        Envelope envelope = sampleEnvelope();
        given(ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container))
            .willReturn(emptyList());

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result).isEqualTo(AutoCaseUpdateResult.ABANDONED);

        verifyNoMoreInteractions(caseUpdateDataService);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(caseDataBuilder);
    }

    @Test
    void should_abandon_update_if_more_than_one_case_is_found() {
        // given
        Envelope envelope = sampleEnvelope();
        given(ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container))
            .willReturn(List.of(1L, 2L));

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result).isEqualTo(AutoCaseUpdateResult.ABANDONED);

        verifyNoMoreInteractions(caseUpdateDataService);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(caseDataBuilder);
    }

    @Test
    void should_return_error_if_ccd_operation_fails() {
        // given
        Envelope envelope = sampleEnvelope();
        given(ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container))
            .willThrow(RuntimeException.class);

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result).isEqualTo(AutoCaseUpdateResult.ERROR);
    }

    @Test
    void should_update_case_if_only_one_case_is_found() {
        // given
        Long existingCaseRef = 1L;
        CaseDetails existingCaseDetails = sampleCaseDetails();
        Envelope envelope = sampleEnvelope();
        SuccessfulUpdateResponse updateDataResponse = sampleUpdateDataResponse();

        given(ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container))
            .willReturn(List.of(existingCaseRef));

        given(ccdApi.getCase(existingCaseRef.toString(), envelope.jurisdiction))
            .willReturn(existingCaseDetails);

        given(caseUpdateDataService.getCaseUpdateData(envelope.container, existingCaseDetails, envelope))
            .willReturn(updateDataResponse);

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result).isEqualTo(AutoCaseUpdateResult.OK);

        // should request data from appropriate service
        verify(caseUpdateDataService)
            .getCaseUpdateData(envelope.container, existingCaseDetails, envelope);

        // should call CCD to update given case
        verify(ccdApi)
            .updateCase(
                eq(existingCaseDetails.getJurisdiction()),
                eq(existingCaseDetails.getCaseTypeId()),
                eq(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR),
                eq(existingCaseRef.toString()),
                caseBuilderCaptor.capture(),
                anyString()
            );

        // and
        Function<StartEventResponse, CaseDataContent> caseBuilder = caseBuilderCaptor.getValue();

        var startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getEventId()).willReturn("event-id");
        given(startEventResponse.getToken()).willReturn("event-token");

        given(
            caseDataBuilder
                .getCaseDataContent(
                    updateDataResponse.caseDetails.caseData,
                    envelope.id,
                    "event-id",
                    "event-token"
                )
        ).willReturn(caseDataContent);

        CaseDataContent caseDataContentSentToCcd = caseBuilder.apply(startEventResponse);

        assertThat(caseDataContentSentToCcd).isEqualTo(caseDataContent);
    }
}
