package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.UUID;
import java.util.function.Function;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordCreatorTest {

    @Mock
    private CcdApi ccdApi;

    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;

    private static final Long CASE_DETAILS_ID = 234L;

    private static final String EVENT_TOKEN = UUID.randomUUID().toString();

    private CreateExceptionRecord exceptionRecordCreator;

    @BeforeEach
    void setUp() {
        exceptionRecordCreator = new CreateExceptionRecord(
            exceptionRecordMapper,
            ccdApi
        );
    }

    @Test
    void should_create_exception_record_when_none_exists_for_the_envelope() {
        // given
        given(ccdApi.getExceptionRecordRefsByEnvelopeId(any(), any())).willReturn(emptyList());
        Envelope envelope = envelope(1);
        ExceptionRecord expectedExceptionRecord = mock(ExceptionRecord.class);
        given(exceptionRecordMapper.mapEnvelope(envelope)).willReturn(expectedExceptionRecord);
        given(ccdApi.authenticateJurisdiction(envelope.jurisdiction)).willReturn(mock(CcdAuthenticator.class));

        // and
        var caseDetails = mock(CaseDetails.class);
        given(ccdApi.createExceptionRecord(any(), anyString(), anyString(), anyString(), any(), anyString()))
            .willReturn(caseDetails);
        given(caseDetails.getId()).willReturn(CASE_DETAILS_ID);

        // and
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        given(startEventResponse.getToken()).willReturn(EVENT_TOKEN);

        // when
        Long ccdRef = exceptionRecordCreator.tryCreateFrom(envelope);

        assertThat(ccdRef).isSameAs(CASE_DETAILS_ID);
        assertExceptionRecordCreated(expectedExceptionRecord, envelope, startEventResponse);

        verify(ccdApi).getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);
        verify(exceptionRecordMapper).mapEnvelope(envelope);
    }

    @Test
    void should_not_create_exception_record_when_one_exists_for_the_envelope() {
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

    @SuppressWarnings("unchecked")
    private void assertExceptionRecordCreated(
        ExceptionRecord expectedExceptionRecord,
        Envelope envelope,
        StartEventResponse startEventResponse
    ) {
        var caseDataBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        String expectedCaseTypeId = String.format("%s_ExceptionRecord", envelope.container.toUpperCase());

        verify(ccdApi).createExceptionRecord(
            any(),
            eq(envelope.jurisdiction),
            eq(expectedCaseTypeId),
            eq("createException"),
            caseDataBuilderCaptor.capture(),
            anyString()
        );

        assertThat(caseDataBuilderCaptor.getValue().apply(startEventResponse))
            .isInstanceOfSatisfying(CaseDataContent.class, caseData ->
                assertThat(caseData.getData()).isSameAs(expectedExceptionRecord)
            );
    }
}
