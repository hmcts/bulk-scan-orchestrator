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
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CcdCaseSubmitterTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Mock
    private TransformationClient transformationClient;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private PaymentsProcessor paymentsProcessor;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    private CcdCaseSubmitter ccdCaseSubmitter;

    @BeforeEach
    void setUp() {
        ccdCaseSubmitter = new CcdCaseSubmitter(
            transformationClient,
            s2sTokenGenerator,
            paymentsProcessor,
            coreCaseDataApi
        );
    }

    @Test
    void should_call_payments_handler_when_case_has_payments() throws Exception {
        // given
        given(transformationClient.transformExceptionRecord(any(),any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        emptyMap()
                    ),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(startCcdEventResp);

        Long newCaseId = 123L;
        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(newCaseId).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

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

        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, jurisdiction);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(CASE_ID)
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );

        // when
        ProcessResult result =
            ccdCaseSubmitter
                .createNewCase(
                    exceptionRecord,
                    configItem,
                    true,
                    IDAM_TOKEN,
                    USER_ID,
                    caseDetails
                );

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();

        verify(paymentsProcessor).updatePayments(caseDetails, CASE_ID);
    }

    @Test
    void should_call_payments_handler_when_case_has_no_payments() throws Exception {
        // given
        given(transformationClient.transformExceptionRecord(any(),any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        emptyMap()
                    ),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(coreCaseDataApi.startForCaseworker(any(), any(), any(), any(), any(), any()))
            .willReturn(startCcdEventResp);

        Long newCaseId = 123L;
        CaseDetails newCaseDetails = mock(CaseDetails.class);
        doReturn(newCaseId).when(newCaseDetails).getId();

        given(coreCaseDataApi.submitForCaseworker(any(), any(), any(), any(), any(), anyBoolean(), any()))
            .willReturn(newCaseDetails);

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

        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.NO); // no payments!
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, jurisdiction);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(CASE_ID)
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );

        // when
        ProcessResult result =
            ccdCaseSubmitter
                .createNewCase(
                    exceptionRecord,
                    configItem,
                    true,
                    IDAM_TOKEN,
                    USER_ID,
                    caseDetails
                );

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();

        verify(paymentsProcessor).updatePayments(caseDetails, CASE_ID);
    }

    @Test
    void should_throw_InvalidCaseDataException_when_transformation_client_returns_422()
        throws IOException, CaseTransformationException {
        // given
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        //setUpTransformationUrl();
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

        Map<String, Object> data = new HashMap<>();
        // putting 6 via `ImmutableMap` is available from Java 9
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "Form1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "some doc"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("some key", "some value"));

        CaseDetails caseDetails = TestCaseBuilder.createCaseWith(builder -> builder
            .id(CASE_ID)
            .caseTypeId(CASE_TYPE_ID)
            .jurisdiction("some jurisdiction")
            .data(data)
        );

        // when
        ProcessResult result = ccdCaseSubmitter.createNewCase(
            exceptionRecord,
            configItem,
            true,
            IDAM_TOKEN,
            USER_ID,
            caseDetails
        );

        // then
        assertThat(result.getModifiedFields()).isEmpty();
        assertThat(result.getWarnings()).containsOnly("warning");
        assertThat(result.getErrors()).containsOnly("error");
    }
}
