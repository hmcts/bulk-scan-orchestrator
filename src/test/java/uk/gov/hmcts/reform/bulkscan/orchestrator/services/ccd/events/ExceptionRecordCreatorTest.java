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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@ExtendWith(MockitoExtension.class)
public class ExceptionRecordCreatorTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;

    @BeforeEach
    public void setUp() {
        setupCcdApi();
    }

    @Test
    public void should_create_exception_record_when_duplicate_prevention_not_supported() {
        // given
        Envelope envelope = envelope(1);
        ExceptionRecord expectedExceptionRecord = mock(ExceptionRecord.class);
        given(exceptionRecordMapper.mapEnvelope(envelope)).willReturn(expectedExceptionRecord);

        // when
        exceptionRecordCreator("other-jurisdiction1").tryCreateFrom(envelope);

        // then
        // no duplicate search is performed
        verify(ccdApi, never()).getExceptionRecordRefsByEnvelopeId(any(), any());
        verify(exceptionRecordMapper).mapEnvelope(envelope);

        assertExceptionRecordCreated(expectedExceptionRecord, envelope);
    }

    @Test
    public void should_create_exception_record_when_none_exists_for_the_envelope() {
        // given
        given(ccdApi.getExceptionRecordRefsByEnvelopeId(any(), any())).willReturn(emptyList());
        Envelope envelope = envelope(1);
        ExceptionRecord expectedExceptionRecord = mock(ExceptionRecord.class);
        given(exceptionRecordMapper.mapEnvelope(envelope)).willReturn(expectedExceptionRecord);

        // when
        exceptionRecordCreator(envelope.jurisdiction).tryCreateFrom(envelope);

        verify(ccdApi).getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);
        verify(exceptionRecordMapper).mapEnvelope(envelope);

        assertExceptionRecordCreated(expectedExceptionRecord, envelope);
    }

    @Test
    public void should_not_create_exception_record_when_one_exists_for_the_envelope() {
        // given
        long existingExceptionRecordId = 12345;
        given(ccdApi.getExceptionRecordRefsByEnvelopeId(any(), any()))
            .willReturn(newArrayList(existingExceptionRecordId));

        Envelope envelope = envelope(1);

        // when
        exceptionRecordCreator(envelope.jurisdiction).tryCreateFrom(envelope);

        // then
        verify(ccdApi).getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);
        verify(ccdApi).authenticateJurisdiction(envelope.jurisdiction);
        verify(exceptionRecordMapper).mapEnvelope(envelope);
        verifyNoMoreInteractions(ccdApi);
        verifyNoMoreInteractions(exceptionRecordMapper);
    }

    private CreateExceptionRecord exceptionRecordCreator(String... jurisdictionsWithDuplicatePrevention) {
        return new CreateExceptionRecord(
            exceptionRecordMapper,
            ccdApi,
            newArrayList(jurisdictionsWithDuplicatePrevention)
        );
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

        given(ccdApi.submitEvent(any(),any(), any(), any(), any()))
            .willReturn(mock(CaseDetails.class));
    }
}
