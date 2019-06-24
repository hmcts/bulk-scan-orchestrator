package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.CCD_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class AttachExceptionRecordToExistingCaseTest {

    @Value("${test-url}")
    private String testUrl;

    @Value("${document_management.url}")
    private String documentManagementUrl;

    @Value("${document_management.context-path}")
    private String dmContextPath;

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    private String documentUuid;

    @BeforeEach
    void setup() throws Exception {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
        documentUuid = StringUtils.substringAfterLast(dmUrl, "/");
    }

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_no_evidence() throws Exception {
        //given
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList(), Instant.now());
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        // when
        invokeCallbackEndpoint(caseDetails, exceptionRecord, null);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(caseDetails, 1));

        verifyExistingCaseIsUpdatedWithExceptionRecordData(caseDetails, exceptionRecord, 1);
    }

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_evidence_documents() throws Exception {
        //given
        CaseDetails caseDetails =
            ccdCaseCreator.createCase(
                singletonList(
                    new Document("certificate1.pdf", "154565768", "other", null, Instant.now(), documentUuid)
                ),
                Instant.now()
            );
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        // when
        invokeCallbackEndpoint(caseDetails, exceptionRecord, null);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(caseDetails, 2));

        verifyExistingCaseIsUpdatedWithExceptionRecordData(caseDetails, exceptionRecord, 1);
    }

    @Test
    public void should_attach_exception_record_to_case_by_legacy_id() throws Exception {
        verifyExceptionRecordAttachesToCase(EXTERNAL_CASE_REFERENCE);
    }

    @Test
    public void should_attach_exception_record_to_case_by_ccd_search_case_reference() throws Exception {
        verifyExceptionRecordAttachesToCase(CCD_CASE_REFERENCE);
    }

    @Test
    public void should_attach_exception_record_to_case_by_attach_to_case_reference() throws Exception {
        verifyExceptionRecordAttachesToCase(null);
    }

    /**
     * Checks if the service allows for attaching an exception record to a case using given
     * reference type - CCD ID, external ID or no type provided.
     *
     * @param searchCaseReferenceType Specifies how the exception record should reference the case.
     *      Possible values can be found in
     *      @see uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes
     *      and correspond to ReferenceType list in exception record definition.
     *      If null, case is referenced the old way - via attachToCaseReference field
     */
    private void verifyExceptionRecordAttachesToCase(String searchCaseReferenceType) throws Exception {
        //given
        CaseDetails caseDetails = ccdCaseCreator.createCase(emptyList());

        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        // when
        invokeCallbackEndpoint(caseDetails, exceptionRecord, searchCaseReferenceType);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(caseDetails, 1));

        verifyExistingCaseIsUpdatedWithExceptionRecordData(caseDetails, exceptionRecord, 1);
    }

    /**
     * Hits the services callback endpoint with a request to attach exception record to a case.
     *
     * @param searchCaseReferenceType Specifies how the exception record should reference the case.
     *      Possible values can be found in
     *      @see uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes
     *      and correspond to ReferenceType list in exception record definition.
     *      If null, case is referenced the old way - via attachToCaseReference field
     */
    private void invokeCallbackEndpoint(
        CaseDetails targetCaseDetails,
        CaseDetails exceptionRecord,
        String searchCaseReferenceType
    ) {
        Map<String, Object> exceptionRecordDataWithSearchFields = exceptionRecordDataWithSearchFields(
            targetCaseDetails,
            exceptionRecord,
            searchCaseReferenceType
        );

        CaseDetails exceptionRecordWithSearchFields =
            exceptionRecord.toBuilder().data(exceptionRecordDataWithSearchFields).build();

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId("attachToExistingCase")
            .caseDetails(exceptionRecordWithSearchFields)
            .build();

        RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .body(callbackRequest)
            .when()
            .post("/callback/attach_case")
            .jsonPath()
            .getList("errors")
            .isEmpty();
    }

    private Map<String, Object> exceptionRecordDataWithSearchFields(
        CaseDetails targetCaseDetails,
        CaseDetails exceptionRecord,
        String searchCaseReferenceType
    ) {
        Map<String, Object> exceptionRecordData = new HashMap<>(exceptionRecord.getData());

        if (searchCaseReferenceType == null) {
            exceptionRecordData.put("attachToCaseReference", String.valueOf(targetCaseDetails.getId()));
        } else {
            exceptionRecordData.put("searchCaseReferenceType", searchCaseReferenceType);

            String searchCaseReference = searchCaseReferenceType.equals(EXTERNAL_CASE_REFERENCE)
                ? (String) targetCaseDetails.getData().get("legacyId")
                : targetCaseDetails.getId().toString();

            exceptionRecordData.put("searchCaseReference", searchCaseReference);
        }

        return exceptionRecordData;
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        UUID poBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, dmUrl);

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> lookUpExceptionRecord(poBox).isPresent());

        return lookUpExceptionRecord(poBox).get();
    }

    private Optional<CaseDetails> lookUpExceptionRecord(UUID randomPoBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.JURSIDICTION + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );
        return caseDetailsList.stream().findFirst();
    }

    private Boolean isExceptionRecordAttachedToTheCase(CaseDetails caseDetails, int expectedScannedDocsSize) {

        CaseDetails updatedCase = ccdApi.getCase(
            String.valueOf(caseDetails.getId()),
            caseDetails.getJurisdiction()
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCase);

        return updatedScannedDocuments.size() == expectedScannedDocsSize;
    }

    private void verifyExistingCaseIsUpdatedWithExceptionRecordData(
        CaseDetails originalCase,
        CaseDetails exceptionRecord,
        int expectedExceptionRecordsSize
    ) {
        CaseDetails updatedCase = ccdApi.getCase(
            String.valueOf(originalCase.getId()),
            originalCase.getJurisdiction()
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCase);

        List<ScannedDocument> exceptionRecordDocuments = getScannedDocuments(exceptionRecord);

        assertThat(updatedScannedDocuments).isNotEmpty();
        assertThat(exceptionRecordDocuments).isNotEmpty();

        assertThat(updatedScannedDocuments).containsAll(exceptionRecordDocuments);

        isExceptionReferenceAttachedToTheScannedDocuments(
            updatedScannedDocuments,
            exceptionRecord.getId(),
            expectedExceptionRecordsSize
        );
    }

    private void isExceptionReferenceAttachedToTheScannedDocuments(
        List<ScannedDocument> updatedScannedDocuments,
        Long id,
        int expectedDocumentsSize
    ) {
        List<ScannedDocument> scannedDocuments = updatedScannedDocuments
            .stream()
            .filter(document -> String.valueOf(id).equals(document.exceptionReference))
            .collect(Collectors.toList());

        assertThat(scannedDocuments.size()).isEqualTo(expectedDocumentsSize);
    }

}
