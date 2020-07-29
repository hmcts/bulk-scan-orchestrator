package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class CaseFinderTest {

    private static final String JURISDICTION = "BULKSCAN";
    private static final String SERVICE = "bulkscan";
    private static final String CASE_REF = "123123123";
    private static final String LEGACY_CASE_REF = "legacy-id-123";

    @Mock
    private CcdApi ccdApi;

    private CaseFinder caseFinder;

    @BeforeEach
    public void setUp() {
        caseFinder = new CaseFinder(ccdApi);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_call_appropriate_api_method_based_on_service_config(boolean searchCasesByEnvelopeId) {
        // given
        var serviceCfg = mock(ServiceConfigItem.class);
        given(serviceCfg.getSearchCasesByEnvelopeId()).willReturn(searchCasesByEnvelopeId);
        given(serviceCfg.getService()).willReturn("some-service-name");

        var exceptionRecord = new ExceptionRecord(
            "er-id",
            null,
            "envelope-id",
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            emptyList(),
            emptyList()
        );

        // when
        caseFinder.findCases(exceptionRecord, serviceCfg);

        // then
        if (searchCasesByEnvelopeId) {
            verify(ccdApi).getCaseRefsByEnvelopeId("envelope-id", "some-service-name");
        } else {
            verify(ccdApi).getCaseRefsByBulkScanCaseReference("er-id", "some-service-name");
        }
    }

    @Test
    public void should_search_case_by_ccd_id_when_envelope_has_it() {
        given(ccdApi.getCase(CASE_REF, JURISDICTION))
            .willReturn(CaseDetails.builder().build());

        caseFinder.findCase(
            envelope(CASE_REF, null)
        );

        verify(ccdApi).getCase(CASE_REF, JURISDICTION);
    }

    @Test
    public void should_return_case_when_found_by_ccd_id() {
        CaseDetails expectedCase = mock(CaseDetails.class);
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(expectedCase);

        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(CASE_REF, LEGACY_CASE_REF)
        );

        assertThat(result).hasValue(expectedCase);
    }

    @Test
    public void should_return_empty_when_case_not_found_by_ccd_id_and_legacy_id_is_absent() {
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willThrow(
            new CaseNotFoundException("Case not found")
        );

        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(CASE_REF, null)
        );

        assertThat(result).isEmpty();
    }

    @Test
    public void should_return_empty_when_neither_id_is_present() {
        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(null, null)
        );

        assertThat(result).isEmpty();
    }

    @Test
    public void should_search_case_by_legacy_id_when_legacy_id_is_present_and_ccd_id_is_not() {
        // given
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(emptyList());

        // when
        caseFinder.findCase(
            envelope(null, LEGACY_CASE_REF)
        );

        // then
        verify(ccdApi).getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE);
        verifyNoMoreInteractions(ccdApi);
    }

    @Test
    public void should_search_case_by_legacy_id_when_not_found_by_ccd_id() {
        // given
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willThrow(
            new CaseNotFoundException("Case not found")
        );

        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(emptyList());

        // when
        caseFinder.findCase(
            envelope(CASE_REF, LEGACY_CASE_REF)
        );

        // then
        InOrder inOrder = inOrder(ccdApi);
        inOrder.verify(ccdApi).getCase(CASE_REF, JURISDICTION);
        inOrder.verify(ccdApi).getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void should_search_case_by_legacy_id_when_ccd_id_is_rejected_by_ccd() {
        // given
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willThrow(
            new InvalidCaseIdException("Invalid case ID", null)
        );

        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(emptyList());

        // when
        caseFinder.findCase(
            envelope(CASE_REF, LEGACY_CASE_REF)
        );

        // then
        InOrder inOrder = inOrder(ccdApi);
        inOrder.verify(ccdApi).getCase(CASE_REF, JURISDICTION);
        inOrder.verify(ccdApi).getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void should_return_empty_when_legacy_id_search_has_no_results() {
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(emptyList());

        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(null, LEGACY_CASE_REF)
        );

        assertThat(result).isEmpty();
    }

    @Test
    public void should_retrieve_case_by_ccd_id_when_search_by_legacy_id_has_one_result() {
        // given
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(Arrays.asList(Long.parseLong(CASE_REF)));

        CaseDetails expectedCase = CaseDetails.builder().build();

        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(expectedCase);

        // when
        caseFinder.findCase(
            envelope(null, LEGACY_CASE_REF)
        );

        // then
        InOrder inOrder = inOrder(ccdApi);
        inOrder.verify(ccdApi).getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE);
        inOrder.verify(ccdApi).getCase(CASE_REF, JURISDICTION);
    }

    @Test
    public void should_return_case_when_found_by_legacy_id() {
        // given
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(Arrays.asList(Long.parseLong(CASE_REF)));

        CaseDetails expectedCase = CaseDetails.builder().build();
        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willReturn(expectedCase);

        // when
        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(null, LEGACY_CASE_REF)
        );

        // then
        assertThat(result).hasValue(expectedCase);
    }

    @Test
    public void should_return_empty_when_case_retrieval_based_on_legacy_id_results_does_not_find_case() {
        // given
        given(ccdApi.getCaseRefsByLegacyId(LEGACY_CASE_REF, SERVICE))
            .willReturn(Arrays.asList(Long.parseLong(CASE_REF)));

        given(ccdApi.getCase(CASE_REF, JURISDICTION)).willThrow(
            new CaseNotFoundException("Case not found")
        );

        // when
        Optional<CaseDetails> result = caseFinder.findCase(
            envelope(null, LEGACY_CASE_REF)
        );

        // then
        assertThat(result).isEmpty();
    }

    private Envelope envelope(String caseRef, String legacyCaseRef) {
        return new Envelope(
            "id123",
            caseRef,
            legacyCaseRef,
            "pobox123",
            JURISDICTION,
            SERVICE,
            "zip-file-name.zip",
            "formtype_1",
            Instant.now(),
            Instant.now(),
            Classification.SUPPLEMENTARY_EVIDENCE,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        );
    }
}
