package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@ExtendWith(MockitoExtension.class)
public class ExceptionRecordCreatorTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;

    private static final Long CASE_DETAILS_ID = 234L;

    private CreateExceptionRecord exceptionRecordCreator;

    @BeforeEach
    public void setUp() {
        exceptionRecordCreator = new CreateExceptionRecord(
            exceptionRecordMapper,
            ccdApi
        );
    }

    @Test
    public void should_create_exception_record_when_none_exists_for_the_envelope() {
        // given
        setupCcdApi();

        given(ccdApi.getExceptionRecordRefsByEnvelopeId(any(), any())).willReturn(emptyList());
        Envelope envelope = envelope(1);
        ExceptionRecord expectedExceptionRecord = mock(ExceptionRecord.class);
        given(exceptionRecordMapper.mapEnvelope(envelope)).willReturn(expectedExceptionRecord);

        // when
        Long ccdRef = exceptionRecordCreator.tryCreateFrom(envelope);

        assertThat(ccdRef).isSameAs(CASE_DETAILS_ID);
        assertExceptionRecordCreated(expectedExceptionRecord, envelope);

        verify(ccdApi).getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);
        verify(exceptionRecordMapper).mapEnvelope(envelope);
    }

    @Test
    public void should_not_create_exception_record_when_one_exists_for_the_envelope() {
        // given
        Long existingExceptionRecordId = 234L;
        given(ccdApi.getExceptionRecordRefsByEnvelopeId(any(), any()))
            .willReturn(newArrayList(existingExceptionRecordId));

        Envelope envelope = envelope(1);

        // when
        Long ccdRef = exceptionRecordCreator.tryCreateFrom(envelope);

        // then
        assertThat(ccdRef).isSameAs(existingExceptionRecordId);

        verify(ccdApi).getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(exceptionRecordMapper);
    }

    private void assertExceptionRecordCreated(ExceptionRecord expectedExceptionRecord, Envelope envelope) {
        ArgumentCaptor<CaseDataContent> caseDataContentArgumentCaptor = ArgumentCaptor.forClass(CaseDataContent.class);
        String expectedCaseTypeId = String.format("%s_ExceptionRecord", envelope.container.toUpperCase());

        verify(ccdApi).submitEvent(
            any(),
            eq(envelope.jurisdiction),
            eq(expectedCaseTypeId),
            eq(null),
            caseDataContentArgumentCaptor.capture()
        );

        assertThat(caseDataContentArgumentCaptor.getValue()).isNotNull();
        assertThat(caseDataContentArgumentCaptor.getValue().getData()).isSameAs(expectedExceptionRecord);
    }

    private void setupCcdApi() {
        given(ccdApi.startEvent(any(), any(), any(), any(), any()))
            .willReturn(mock(StartEventResponse.class));

        CaseDetails caseDetails = mock(CaseDetails.class);
        given(caseDetails.getId()).willReturn(CASE_DETAILS_ID);
        given(ccdApi.submitEvent(any(),any(), any(), any(), any()))
            .willReturn(caseDetails);
    }
}
