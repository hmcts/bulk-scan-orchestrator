package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.caseupdatedetails.CaseUpdateDetailsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleEnvelope;

@ExtendWith(MockitoExtension.class)
class AutoCaseUpdaterTest {

    @Mock CaseUpdateDetailsService caseUpdateDataService;
    @Mock CcdApi ccdApi;
    @Mock CaseDataUpdater caseDataUpdater;

    AutoCaseUpdater service;

    @BeforeEach
    void setUp() {
        this.service = new AutoCaseUpdater(caseUpdateDataService, ccdApi, caseDataUpdater);
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
        verifyNoMoreInteractions(caseDataUpdater);
    }

    @Test
    void should_abandon_update_if_more_than_one_case_is_found() {
        // given
        Envelope envelope = sampleEnvelope();
        given(ccdApi.getCaseRefsByEnvelopeId(envelope.id, envelope.container))
            .willReturn(asList(1L, 2L));

        // when
        var result = service.updateCase(envelope);

        // then
        assertThat(result).isEqualTo(AutoCaseUpdateResult.ABANDONED);

        verifyNoMoreInteractions(caseUpdateDataService);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(caseDataUpdater);
    }
}
