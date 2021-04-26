package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResultRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult.attachToCaseCaseRequest;

@ExtendWith(MockitoExtension.class)
class CallbackResultRepositoryProxyTest {
    private static final int RETRY_COUNT = 3;
    private static final String CASE_ID = "CASE_ID";
    private static final String EXCEPTION_RECORD_ID = "ER_ID";

    private CallbackResultRepositoryProxy callbackResultRepositoryProxy;

    @Mock
    private CallbackResultRepository callbackResultRepository;

    @BeforeEach
    void setUp() {
        callbackResultRepositoryProxy = new CallbackResultRepositoryProxy(callbackResultRepository, RETRY_COUNT);
    }

    @Test
    void should_not_retry_calling_repo_if_no_exception_thrown() {
        // given
        given(callbackResultRepository.insert(any(NewCallbackResult.class)))
            .willReturn(UUID.randomUUID());
        NewCallbackResult callbackResult = attachToCaseCaseRequest(EXCEPTION_RECORD_ID, CASE_ID);

        // when
        callbackResultRepositoryProxy.storeCallbackResult(callbackResult);

        // then
        verify(callbackResultRepository, times(1)).insert(callbackResult);
    }

    @Test
    void should_try_two_times_calling_repo_if_one_exception_thrown() {
        // given
        given(callbackResultRepository.insert(any(NewCallbackResult.class)))
            .willThrow(new RuntimeException())
            .willReturn(UUID.randomUUID());
        NewCallbackResult callbackResult = attachToCaseCaseRequest(EXCEPTION_RECORD_ID, CASE_ID);

        // when
        callbackResultRepositoryProxy.storeCallbackResult(callbackResult);

        // then
        verify(callbackResultRepository, times(2)).insert(callbackResult);
    }

    @Test
    void should_try_three_times_calling_repo_if_two_exceptions_thrown() {
        // given
        given(callbackResultRepository.insert(any(NewCallbackResult.class)))
            .willThrow(new RuntimeException())
            .willReturn(UUID.randomUUID());
        NewCallbackResult callbackResult = attachToCaseCaseRequest(EXCEPTION_RECORD_ID, CASE_ID);

        // when
        callbackResultRepositoryProxy.storeCallbackResult(callbackResult);

        // then
        verify(callbackResultRepository, times(2)).insert(callbackResult);
    }

    @Test
    void should_try_three_times_calling_repo_if_three_exceptions_thrown() {
        // given
        given(callbackResultRepository.insert(any(NewCallbackResult.class)))
            .willThrow(new RuntimeException())
            .willThrow(new RuntimeException())
            .willThrow(new RuntimeException());
        NewCallbackResult callbackResult = attachToCaseCaseRequest(EXCEPTION_RECORD_ID, CASE_ID);

        // when
        callbackResultRepositoryProxy.storeCallbackResult(callbackResult);

        // then
        verify(callbackResultRepository, times(3)).insert(callbackResult);
    }
}
