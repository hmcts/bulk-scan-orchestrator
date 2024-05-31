package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@ExtendWith(MockitoExtension.class)
class CcdApiTest {

    private static final String EXCEPTION_RECORD_ID = "1";
    private static final long EXISTING_CASE_ID = 1L;
    private static final String EXISTING_CASE_TYPE_ID = "existing_case_type";
    private static final String SERVICE_NAME = "some_service_name";

    @Mock
    private CoreCaseDataApi feignCcdApi;

    @Mock
    private CcdAuthenticatorFactory authenticatorFactory;

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    @Mock
    private CaseDetails caseDetails;

    private CcdApi ccdApi;

    @BeforeEach
    void setUp() {
        ccdApi = new CcdApi(feignCcdApi, authenticatorFactory, serviceConfigProvider);
    }

    @Test
    void start_event_should_start_event_with_correct_data() {
        // given
        var userToken = "userToken";
        var serviceToken = "serviceToken";
        var userId = "userId";
        var jurisdiction = "jurisdiction1";
        var caseTypeId = "caseTypeId1";
        var eventId = "eventId1";
        var caseId = "123-123-123";

        var startEventResponse = StartEventResponse.builder().build();
        given(feignCcdApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        ))
            .willReturn(startEventResponse);

        // when
        ccdApi.startEventForCaseWorker(
            new CcdRequestCredentials(userToken, serviceToken, userId),
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        );

        // then
        verify(feignCcdApi).startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        );
    }

    @Test
    void start_event_should_rethrow_exception_when_communication_with_ccd_fails() {
        // given
        var userToken = "userToken";
        var serviceToken = "serviceToken";
        var userId = "userId";
        var jurisdiction = "jurisdiction1";
        var caseTypeId = "caseTypeId1";
        var eventId = "eventId1";
        var caseId = "123-123-123";

        var ccdException = mock(FeignException.BadRequest.class);

        given(feignCcdApi.startEventForCaseWorker(
            userToken,
            serviceToken,
            userId,
            jurisdiction,
            caseTypeId,
            caseId,
            eventId
        ))
            .willThrow(ccdException);

        // when
        Throwable exc = catchThrowable(
            () -> ccdApi.startEventForCaseWorker(
                new CcdRequestCredentials(userToken, serviceToken, userId),
                jurisdiction,
                caseTypeId,
                caseId,
                eventId
            )
        );

        // then
        assertThat(exc).isSameAs(ccdException);
    }

    @Test
    void updateCaseInCcd_should_handle_feign_exception() {
        // given
        final FeignException.BadRequest ex = mock(FeignException.BadRequest.class);

        given(feignCcdApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willThrow(ex);
        given(caseDetails.getId()).willReturn(1L);
        given(caseDetails.getCaseTypeId()).willReturn(EXISTING_CASE_TYPE_ID);

        final CcdRequestCredentials ccdRequestCredentials =
                new CcdRequestCredentials("idamToken", "serviceToken", "userId");
        final ExceptionRecord exceptionRecord = getExceptionRecord();
        final CaseDataContent caseDataContent = CaseDataContent.builder().build();

        // when
        CcdCallException exception = catchThrowableOfType(
            () -> ccdApi.updateCaseInCcd(
                true,
                ccdRequestCredentials,
                exceptionRecord,
                caseDetails,
                caseDataContent
            ),
            CcdCallException.class
        );

        // then
        assertThat(exception.getCause()).isSameAs(ex);
    }

    @Test
    void updateCaseInCcd_should_return_result_from_ccd() {
        // given
        final CaseDetails updatedCaseDetails = mock(CaseDetails.class);
        given(feignCcdApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willReturn(updatedCaseDetails);
        given(caseDetails.getId()).willReturn(1L);
        given(caseDetails.getCaseTypeId()).willReturn(EXISTING_CASE_TYPE_ID);

        final CcdRequestCredentials ccdRequestCredentials =
                new CcdRequestCredentials("idamToken", "serviceToken", "userId");
        final ExceptionRecord exceptionRecord = getExceptionRecord();
        final CaseDataContent caseDataContent = CaseDataContent.builder().build();

        // when
        CaseDetails res = ccdApi.updateCaseInCcd(
                true,
                ccdRequestCredentials,
                exceptionRecord,
                caseDetails,
                caseDataContent
            );

        // then
        assertThat(res).isSameAs(updatedCaseDetails);
    }

    @Test
    void updateCaseInCcd_should_rethrow_feign_conflict() {
        // given
        final FeignException.Conflict conflict = mock(FeignException.Conflict.class);

        given(feignCcdApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        )).willThrow(conflict);
        given(caseDetails.getId()).willReturn(1L);
        given(caseDetails.getCaseTypeId()).willReturn(EXISTING_CASE_TYPE_ID);

        final ExceptionRecord exceptionRecord = getExceptionRecord();
        final CcdRequestCredentials ccdRequestCredentials =
                new CcdRequestCredentials("idamToken", "serviceToken", "userId");
        final CaseDataContent caseDataContent = CaseDataContent.builder().build();

        // when
        FeignException.Conflict exception = catchThrowableOfType(
            () -> ccdApi.updateCaseInCcd(
                true,
                ccdRequestCredentials,
                exceptionRecord,
                caseDetails,
                caseDataContent
            ),
            FeignException.Conflict.class
        );

        // then
        assertThat(exception).isSameAs(conflict);
    }

    @Test
    void updateCaseInCcd_should_handle_feign_unprocessable_entity() {
        // given
        given(feignCcdApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyBoolean(),
            any(CaseDataContent.class)
        ))
            .willThrow(
                new FeignException.UnprocessableEntity(
                    "Msg",
                    mock(Request.class),
                    "Body".getBytes(),
                    null
                )
            );
        given(caseDetails.getId()).willReturn(EXISTING_CASE_ID);
        given(caseDetails.getCaseTypeId()).willReturn(EXISTING_CASE_TYPE_ID);

        final ExceptionRecord exceptionRecord = getExceptionRecord();
        final CcdRequestCredentials ccdRequestCredentials =
                new CcdRequestCredentials("idamToken", "serviceToken", "userId");
        final CaseDataContent caseDataContent = CaseDataContent.builder().build();

        // when
        CcdCallException exception = catchThrowableOfType(
            () -> ccdApi.updateCaseInCcd(
                true,
                ccdRequestCredentials,
                exceptionRecord,
                caseDetails,
                caseDataContent
            ),
            CcdCallException.class
        );

        // then
        assertThat(exception)
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessage(
                "CCD returned 422 Unprocessable Entity response when trying to update case for %s "
                    + "jurisdiction with case Id %s based on exception record with Id %s. CCD response: Body",
                SERVICE_NAME,
                EXISTING_CASE_ID,
                exceptionRecord.id
            );
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            EXCEPTION_RECORD_ID,
            EXISTING_CASE_TYPE_ID,
            "envelopeId123",
            "12345",
            SERVICE_NAME,
            SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "Form1",
            now(),
            now(),
            singletonList(new ScannedDocument(
                DocumentType.FORM,
                "D8",
                new DocumentUrl(
                    "http://locahost",
                    "http://locahost/binary",
                    "file1.pdf"
                ),
                "1234",
                "file1.pdf",
                now(),
                now()
            )),
            emptyList()
        );
    }
}
