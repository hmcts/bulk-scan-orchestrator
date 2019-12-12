package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.CcdCallbackController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("nosb")
    // no servicebus queue handler registration
class AttachExceptionRecordWithOcrToExistingCaseTest {

    @Value("${test-url}")
    String testUrl;

    @Value("${document_management.url}")
    String documentManagementUrl;

    @Value("${document_management.context-path}")
    String dmContextPath;

    @Autowired CcdApi ccdApi;
    @Autowired CcdCaseCreator ccdCaseCreator;
    @Autowired CaseSearcher caseSearcher;
    @Autowired EnvelopeMessager envelopeMessager;
    @Autowired DocumentManagementUploadService dmUploadService;
    @Autowired CcdAuthenticatorFactory ccdAuthenticatorFactory;

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_no_evidence() throws Exception {
        //given
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now());
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        CcdAuthenticator ccdAuthenticator = ccdAuthenticatorFactory.createForJurisdiction(SampleData.JURSIDICTION);


        // when
        Response callResp = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .header(AUTHORIZATION, ccdAuthenticator.getUserToken())
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserDetails().getId())
            .body(callbackRequest)
            .when()
            .post("/callback/attach_case")
            .thenReturn();
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        String poBox = UUID.randomUUID().toString();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, "localhost");

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> lookUpExceptionRecord(poBox).isPresent());

        return lookUpExceptionRecord(poBox).get();
    }

    private Optional<CaseDetails> lookUpExceptionRecord(String poBox) {
        return caseSearcher
            .search(
                SampleData.JURSIDICTION,
                SampleData.JURSIDICTION + "_" + CreateExceptionRecord.CASE_TYPE,
                ImmutableMap.of(
                    "case.poBox", poBox
                )
            )
            .stream()
            .findFirst();
    }
}
