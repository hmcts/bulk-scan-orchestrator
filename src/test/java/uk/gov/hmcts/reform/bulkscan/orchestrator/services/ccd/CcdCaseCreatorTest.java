package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.CaseTransformationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.TransformationClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;

import java.io.IOException;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CcdCaseCreatorTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private CcdCaseCreator ccdCaseCreator;

    @BeforeEach
    void setUp() {
        ccdCaseCreator = new CcdCaseCreator(
            transformationClient,
            s2sTokenGenerator,
            coreCaseDataApi
        );
    }

    @Test
    void should_throw_InvalidCaseDataException_when_transformation_client_returns_422()
        throws IOException, CaseTransformationException {
        // given
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        InvalidCaseDataException exception = new InvalidCaseDataException(
            new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY),
            new TransformationErrorResponse(singletonList("error"), singletonList("warning"))
        );
        doThrow(exception)
            .when(transformationClient)
            .transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString());

        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl("url");
        ExceptionRecord exceptionRecord = new ExceptionRecord(
            "1",
            CASE_TYPE_ID,
            "12345",
            "some jurisdiction",
            EXCEPTION,
            "Form1",
            now(),
            now(),
            emptyList(),
            emptyList()
        );

        // when
        ProcessResult result = ccdCaseCreator.createNewCase(
            exceptionRecord,
            configItem,
            true,
            IDAM_TOKEN,
            USER_ID
        );

        // then
        assertThat(result.getModifiedFields()).isEmpty();
        assertThat(result.getWarnings()).containsOnly("warning");
        assertThat(result.getErrors()).containsOnly("error");
    }
}
