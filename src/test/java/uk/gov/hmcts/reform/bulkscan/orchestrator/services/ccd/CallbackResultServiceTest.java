package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

@ExtendWith(MockitoExtension.class)
class CallbackResultServiceTest {

    private static final String EXCEPTION_RECORD_ID = "ER_ID";
    private static final String CASE_ID = "CASE_ID";

    @Mock
    private CallbackResultRepository callbackResultRepository;

    private CallbackResultService callbackResultService;

    private static final List<CallbackResult> results =
        singletonList(
            new CallbackResult(UUID.randomUUID(), Instant.now(), CREATE_CASE, EXCEPTION_RECORD_ID, CASE_ID)
        );
    private static final NullPointerException exception = new NullPointerException("msg");

    @BeforeEach
    void setUp() {
        callbackResultService = new CallbackResultService(callbackResultRepository);
    }

    @Test
    void should_find_by_exception_record_id() {
        // given
        given(callbackResultRepository.findByExceptionRecordId(EXCEPTION_RECORD_ID)).willReturn(results);

        // when
        List<CallbackResult> res = callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID);

        // then
        assertThat(res).isSameAs(results);
        verify(callbackResultRepository).findByExceptionRecordId(EXCEPTION_RECORD_ID);
    }

    @Test
    void should_rethrow_when_find_by_exception_record_id_throws() {
        // given
        given(callbackResultRepository.findByExceptionRecordId(EXCEPTION_RECORD_ID)).willThrow(exception);

        // when
        Throwable throwable = catchThrowable(() -> callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID));

        // then
        assertThat(throwable).isSameAs(exception);
        verify(callbackResultRepository).findByExceptionRecordId(EXCEPTION_RECORD_ID);
    }

    @Test
    void should_find_by_exception_case_id() {
        // given
        given(callbackResultRepository.findByCaseId(CASE_ID)).willReturn(results);

        // when
        List<CallbackResult> res = callbackResultService.findByCaseId(CASE_ID);

        // then
        assertThat(res).isSameAs(results);
        verify(callbackResultRepository).findByCaseId(CASE_ID);
    }

    @Test
    void should_rethrow_when_find_by_case_id_throws() {
        // given
        given(callbackResultRepository.findByCaseId(CASE_ID)).willThrow(exception);

        // when
        Throwable throwable = catchThrowable(() -> callbackResultService.findByCaseId(CASE_ID));

        // then
        assertThat(throwable).isSameAs(exception);
        verify(callbackResultRepository).findByCaseId(CASE_ID);
    }
}
