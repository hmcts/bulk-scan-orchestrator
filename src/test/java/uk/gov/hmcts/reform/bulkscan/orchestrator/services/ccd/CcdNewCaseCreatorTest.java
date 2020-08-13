package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.ServiceResponseParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.CaseDataTransformer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.CaseCreationDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CreateCaseResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.time.LocalDateTime.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;

@ExtendWith(MockitoExtension.class)
class CcdNewCaseCreatorTest {

    private static final String IDAM_TOKEN = "idam-token";
    private static final String USER_ID = "user-id";
    private static final String SERVICE = "service";
    private static final long CASE_ID = 123;
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Mock
    private CaseDataTransformer caseDataTransformer;

    @Mock
    private ServiceResponseParser serviceResponseParser;

    @Mock
    private AuthTokenGenerator s2sTokenGenerator;

    @Mock
    private CcdApi ccdApi;

    private CcdNewCaseCreator ccdNewCaseCreator;

    @Mock
    private HttpClientErrorException.UnprocessableEntity unprocessableEntity;

    @BeforeEach
    void setUp() {
        ccdNewCaseCreator = new CcdNewCaseCreator(
            caseDataTransformer,
            serviceResponseParser,
            s2sTokenGenerator,
            ccdApi
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_return_new_case_id_when_successfully_executed_all_the_steps() {
        // given
        given(s2sTokenGenerator.generate()).willReturn(randomUUID().toString());
        given(caseDataTransformer.transformExceptionRecord(any(), any(), any()))
            .willReturn(
                new SuccessfulTransformationResponse(
                    new CaseCreationDetails(
                        "some_case_type",
                        "some_event_id",
                        basicCaseData()
                    ),
                    emptyList()
                )
            );

        StartEventResponse startCcdEventResp = mock(StartEventResponse.class);

        given(ccdApi.createCase(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()
        )).willReturn(CASE_ID);

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        // when
        CreateCaseResult result =
            ccdNewCaseCreator
                .createNewCase(
                    exceptionRecord,
                    configItem,
                    true,
                    IDAM_TOKEN,
                    USER_ID
                );

        // then
        assertThat(result.caseId).isEqualTo(CASE_ID);

        // and
        var caseDetailsBuilderCaptor = ArgumentCaptor.forClass(Function.class);
        verify(ccdApi).createCase(
            eq(IDAM_TOKEN),
            anyString(),
            eq(USER_ID),
            eq(exceptionRecord.poBoxJurisdiction),
            anyString(),
            anyString(),
            caseDetailsBuilderCaptor.capture(),
            anyString()
        );
        assertThat(caseDetailsBuilderCaptor.getValue().apply(startCcdEventResp))
            .isInstanceOf(CaseDataContent.class);
    }

    @Test
    void should_throw_UnprocessableEntityException_when_transformation_client_returns_422() {
        // given
        when(s2sTokenGenerator.generate()).thenReturn(randomUUID().toString());
        given(serviceResponseParser.parseResponseBody(unprocessableEntity))
            .willReturn(new ClientServiceErrorResponse(singletonList("error"), singletonList("warning")));
        doThrow(unprocessableEntity)
            .when(caseDataTransformer)
            .transformExceptionRecord(anyString(), any(ExceptionRecord.class), anyString());

        ServiceConfigItem configItem = getConfigItem();
        ExceptionRecord exceptionRecord = getExceptionRecord();

        // when
        CreateCaseResult result = ccdNewCaseCreator.createNewCase(
            exceptionRecord,
            configItem,
            true,
            IDAM_TOKEN,
            USER_ID
        );

        // then
        assertThat(result.warnings).containsOnly("warning");
        assertThat(result.errors).containsOnly("error");
    }

    private ExceptionRecord getExceptionRecord() {
        return new ExceptionRecord(
            "1",
            CASE_TYPE_ID,
            "envelopeId123",
            "12345",
            "some jurisdiction",
            EXCEPTION,
            "Form1",
            now(),
            now(),
            emptyList(),
            emptyList()
        );
    }

    private Map<String, Object> basicCaseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("poBox", "12345");
        data.put("journeyClassification", EXCEPTION.name());
        data.put("formType", "A1");
        data.put("deliveryDate", "2019-09-06T15:30:03.000Z");
        data.put("openingDate", "2019-09-06T15:30:04.000Z");
        data.put("scannedDocuments", TestCaseBuilder.document("https://url", "name"));
        data.put("scanOCRData", TestCaseBuilder.ocrDataEntry("key", "value"));
        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, "987");
        data.put(ExceptionRecordFields.PO_BOX_JURISDICTION, "sample jurisdiction");
        return data;
    }

    private ServiceConfigItem getConfigItem() {
        ServiceConfigItem configItem = new ServiceConfigItem();
        configItem.setTransformationUrl("url");
        return configItem;
    }
}
