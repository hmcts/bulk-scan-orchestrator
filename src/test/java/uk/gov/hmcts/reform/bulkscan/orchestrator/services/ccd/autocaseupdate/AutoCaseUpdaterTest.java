package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.SuccessfulUpdateResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleCaseDetails;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleEnvelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleUpdateDataResponse;

@ExtendWith(MockitoExtension.class)
class AutoCaseUpdaterTest {

    @Mock CaseUpdateDetailsService caseUpdateDataService;
    @Mock CaseFinder caseFinder;
    @Mock CcdApi ccdApi;
    @Mock CaseDataContentBuilderProvider caseDataBuilder;

    @Mock Function<StartEventResponse, CaseDataContent> caseDataContentBuilder;
    @Mock CaseDataUpdater caseDataUpdater;

    AutoCaseUpdater service;

    @BeforeEach
    void setUp() {
        this.service = new AutoCaseUpdater(caseUpdateDataService, caseFinder, ccdApi, caseDataBuilder, caseDataUpdater);
    }

    @Test
    void should_abandon_update_if_case_is_not_found() {
        // given
        Envelope envelope = sampleEnvelope();
        given(caseFinder.findCase(envelope))
            .willReturn(Optional.empty());

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result.type).isEqualTo(AutoCaseUpdateResultType.ABANDONED);

        verifyNoMoreInteractions(caseUpdateDataService);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(caseDataBuilder);
    }

    @Test
    void should_return_error_if_ccd_operation_fails() {
        // given
        Envelope envelope = sampleEnvelope();
        given(caseFinder.findCase(envelope)).willThrow(RuntimeException.class);

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result.type).isEqualTo(AutoCaseUpdateResultType.ERROR);
    }

    @Test
    void should_update_case_if_it_is_found() {
        // given
        Long existingCaseRef = 1L;
        CaseDetails existingCaseDetails = sampleCaseDetails();
        Envelope envelope = sampleEnvelope();
        SuccessfulUpdateResponse updateDataResponse = sampleUpdateDataResponse();

        given(caseFinder.findCase(envelope))
            .willReturn(Optional.of(existingCaseDetails));

        given(caseUpdateDataService.getCaseUpdateData(envelope.container, existingCaseDetails, envelope))
            .willReturn(updateDataResponse);

        given(
            caseDataUpdater.updateEnvelopeReferences(
                updateDataResponse.caseDetails.caseData,
                envelope.id,
                CaseAction.UPDATE,
                existingCaseDetails.getData())
        ).willReturn(updateDataResponse.caseDetails.caseData);

        given(caseDataBuilder.getBuilder(updateDataResponse.caseDetails.caseData, envelope.id))
            .willReturn(caseDataContentBuilder);

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result.type).isEqualTo(AutoCaseUpdateResultType.OK);
        assertThat(result.caseId).isEqualTo(existingCaseDetails.getId());

        // should request data from appropriate service
        verify(caseUpdateDataService)
            .getCaseUpdateData(envelope.container, existingCaseDetails, envelope);

        // should call CCD to update given case
        verify(ccdApi)
            .updateCase(
                eq(existingCaseDetails.getJurisdiction()),
                eq(existingCaseDetails.getCaseTypeId()),
                eq(EventIds.ATTACH_SCANNED_DOCS_WITH_OCR),
                eq(existingCaseDetails.getId().toString()),
                eq(caseDataContentBuilder),
                anyString()
            );
    }
}
