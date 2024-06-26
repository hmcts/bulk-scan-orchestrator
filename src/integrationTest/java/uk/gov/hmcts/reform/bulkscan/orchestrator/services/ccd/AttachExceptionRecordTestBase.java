package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.GetDocumentHashResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.restassured.http.ContentType.JSON;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_SUBMIT_URL;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_BULK_SCAN;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.CASE_TYPE_EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.Environment.JURISDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.CcdCallbackController.USER_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.AWAITING_PAYMENT_DCN_PROCESSING;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.DISPLAY_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.OCR_DATA_VALIDATION_WARNINGS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;

public class AttachExceptionRecordTestBase {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final long EXCEPTION_RECORD_ID = 26409983479785245L;
    static final String EVENT_ID_ATTACH_TO_CASE = "attachToExistingCase";
    static final String CALLBACK_ATTACH_CASE_PATH = "/callback/attach_case";

    static final String RESPONSE_FIELD_ERRORS = "errors";
    static final String RESPONSE_FIELD_WARNINGS = "warnings";

    static final String DOCUMENT_FILENAME = "document.pdf";
    static final String DOCUMENT_NUMBER = "123456";
    static final Map<String, Object> EXISTING_DOC = document(DOCUMENT_FILENAME, DOCUMENT_NUMBER, EXCEPTION_RECORD_ID);

    private static final String CASE_URL = CASE_SUBMIT_URL + "/" + CASE_REF;
    private static final String START_EVENT_URL = CASE_URL + "/event-triggers/attachScannedDocs/token";

    // see WireMock mapping json files
    private static final String MOCKED_IDAM_TOKEN_SIG = "q6hDG0Z1Qbinwtl8TgeDrAVV0LlCTRtbQqBYoMjd03k";
    private static final String MOCKED_S2S_TOKEN_SIG =
        "X1-LdZAd5YgGFP16-dQrpqEICqRmcu1zL_zeCLyUqMjb5DVx7xoU-r8yXHfgd4tmmjGqbsBz_kLqgu8yruSbtg";
    private static final String MOCKED_USER_ID = "640";
    private static final String SUBMIT_URL = CASE_URL + "/events?ignore-warning=true";

    private static final String EVENT_ID = "someID";
    private static final String EVENT_TOKEN = "theToken";

    private static final String EXCEPTION_RECORD_FILENAME = "record.pdf";
    static final String EXCEPTION_RECORD_DOCUMENT_NUMBER = "654321";
    static final String EXCEPTION_RECORD_DOCUMENT_UUID = "UUID-1";
    static final String EXCEPTION_RECORD_DOCUMENT_HASH = "UUID-1-sasdas-sdada";

    private static final String SERVICE_AUTHORIZATION_HEADER = "ServiceAuthorization";
    private static final String RESPONSE_FIELD_DATA = "data";
    private static final String ATTACH_TO_CASE_REFERENCE_FIELD_NAME = "attachToCaseReference";
    private static final String SCANNED_DOCUMENTS_FIELD_NAME = "scannedDocuments";

    private static final Map<String, Object> CASE_DATA = ImmutableMap.of(
        SCANNED_DOCUMENTS_FIELD_NAME, ImmutableList.of(EXISTING_DOC)
    );

    private static final CaseDetails CASE_DETAILS = CaseDetails.builder()
        .jurisdiction(JURISDICTION)
        .caseTypeId(CASE_TYPE_BULK_SCAN)
        .id(Long.parseLong(CASE_REF))
        .data(CASE_DATA)
        .build();

    static final Map<String, Object> EXCEPTION_RECORD_DOC = document(
        EXCEPTION_RECORD_FILENAME,
        EXCEPTION_RECORD_DOCUMENT_NUMBER
    );

    private static final StartEventResponse START_EVENT_RESPONSE = StartEventResponse
        .builder()
        .caseDetails(CASE_DETAILS)
        .eventId(EVENT_ID)
        .token(EVENT_TOKEN)
        .build();

    @LocalServerPort
    int applicationPort;

    @BeforeEach
    public void before() throws JsonProcessingException {
        WireMock.reset();
        givenThat(ccdStartEvent().willReturn(okJson(MAPPER.writeValueAsString(START_EVENT_RESPONSE))));
        mockCaseSearchByCcdId(CASE_REF, okJson(MAPPER.writeValueAsString(CASE_DETAILS)));
        givenThat(ccdSubmitEvent().willReturn(okJson(MAPPER.writeValueAsString(CASE_DETAILS))));

        mockCaseSearchByCcdId(
            String.valueOf(EXCEPTION_RECORD_ID),
            // an exception record not attached to any case
            okJson(
                MAPPER.writeValueAsString(exceptionRecord(null))
            )
        );
        mockCdamHash(EXCEPTION_RECORD_DOCUMENT_UUID, EXCEPTION_RECORD_DOCUMENT_HASH);
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setPort(applicationPort)
            .setContentType(JSON)
            .build();
    }

    ImmutableMap<String, String> userHeaders() {
        return ImmutableMap.of(
            AUTHORIZATION, MOCKED_IDAM_TOKEN_SIG,
            USER_ID, MOCKED_USER_ID
        );
    }

    void verifyRequestedAttachingToCase() {
        verify(startEventRequest());
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.evidenceHandled",
            WireMock.equalTo("No")
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments.length()",
            WireMock.equalTo("2")
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments[0].value.fileName",
            WireMock.equalTo(DOCUMENT_FILENAME)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.data.scannedDocuments[1].value.fileName",
            WireMock.equalTo(EXCEPTION_RECORD_FILENAME)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.summary",
            WireMock.equalTo(String.format(
                "Attaching exception record(%d) document numbers:[%s] to case:%s",
                EXCEPTION_RECORD_ID,
                EXCEPTION_RECORD_DOCUMENT_NUMBER,
                CASE_REF
            ))
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event.id",
            WireMock.equalTo(EVENT_ID)
        );
        verifyRequestPattern(
            submittedScannedRecords(),
            "$.event_token",
            WireMock.equalTo(EVENT_TOKEN)
        );
    }

    void verifySuccessResponse(
        ValidatableResponse response,
        CallbackRequest request
    ) {
        verifySuccessResponse(response, request, true);
    }

    @SuppressWarnings("unchecked")
    void verifySuccessResponse(
        ValidatableResponse response,
        CallbackRequest request,
        boolean hashTokenValidation
    ) {
        JsonPath responseJson = response.extract().jsonPath();

        assertThat(responseJson.getList(RESPONSE_FIELD_ERRORS)).isNullOrEmpty();
        assertThat(responseJson.getList(RESPONSE_FIELD_WARNINGS)).isNullOrEmpty();

        Map<String, Object> responseData = responseJson.getMap(RESPONSE_FIELD_DATA);
        assertThat(responseData).isNotNull();
        assertThat(responseData.get(ATTACH_TO_CASE_REFERENCE_FIELD_NAME)).isEqualTo(CASE_REF);
        assertThat(responseData.get(DISPLAY_WARNINGS)).isEqualTo(NO);
        assertThat(responseData.get(OCR_DATA_VALIDATION_WARNINGS)).isEqualTo(emptyList());

        List<Map> expectedDocList = (List)responseData.get(SCANNED_DOCUMENTS_FIELD_NAME);

        if (hashTokenValidation) {

            Map<String, String> documentHashMap =
                expectedDocList
                    .stream()
                    .collect(
                        toMap(
                            obj ->
                                ((String) (((Map<String, Object>)
                                    ((Map<String, Object>) obj).get("value")))
                                    .get("controlNumber")),
                            obj -> {
                                String hash = (String) (((Map<String, Object>) (((Map<String, Object>)
                                    (((Map<String, Object>) obj).get("value")))
                                    .get("url"))
                                )
                                    .remove("document_hash")

                                );
                                return hash == null ? "" : hash;
                            }
                        )
                    );

            assertThat(documentHashMap.get(EXCEPTION_RECORD_DOCUMENT_NUMBER)).isEqualTo("");

        }
        assertMapsAreEqualIgnoringFields(
            responseData,
            request.getCaseDetails().getData(),
            ATTACH_TO_CASE_REFERENCE_FIELD_NAME,
            DISPLAY_WARNINGS,
            OCR_DATA_VALIDATION_WARNINGS
        );
    }

    CaseDetails exceptionRecord(String attachToCaseReference) {
        return exceptionRecord(
            attachToCaseReference,
            null,
            null,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC,
            false
        );
    }

    private CaseDetails exceptionRecord(
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId,
        Map<String, Object> document,
        boolean containsPayment
    ) {
        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(EXCEPTION_RECORD_ID)
            .caseTypeId(caseTypeId)
            .data(
                exceptionDataWithDoc(
                    ImmutableList.of(document),
                    attachToCaseReference,
                    searchCaseReferenceType,
                    searchCaseReference,
                    containsPayment
                )
            ).build();
    }

    void mockCaseSearchByCcdId(String ccdId, ResponseDefinitionBuilder responseBuilder) {
        givenThat(
            get("/cases/" + ccdId)
                .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG))
                .willReturn(responseBuilder)
        );
    }

    void mockCdamHash(String documentUuid, String hashToken) throws JsonProcessingException {
        givenThat(
            get("/cases/documents/" + documentUuid + "/token")
                .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG))
                .willReturn(okJson(MAPPER.writeValueAsString(new GetDocumentHashResponse(hashToken))))
        );
    }

    void mockCaseSearchByLegacyId(
        String legacyId,
        ResponseDefinitionBuilder responseBuilder
    ) {
        givenThat(
            post("/searchCases?ctid=" + CASE_TYPE_BULK_SCAN)
                .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
                .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG))
                .withRequestBody(
                    matchingJsonPath("$.query.match_phrase", containing(legacyId))
                )
                .willReturn(responseBuilder)
        );
    }

    MappingBuilder ccdSubmitEvent() {
        return post(SUBMIT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    MappingBuilder ccdStartEvent() {
        return get(START_EVENT_URL)
            .withHeader(AUTHORIZATION, containing(MOCKED_IDAM_TOKEN_SIG))
            .withHeader(SERVICE_AUTHORIZATION_HEADER, containing(MOCKED_S2S_TOKEN_SIG));
    }

    RequestPatternBuilder submittedScannedRecords() {
        return postRequestedFor(urlEqualTo(SUBMIT_URL));
    }

    RequestPatternBuilder startEventRequest() {
        return getRequestedFor(urlEqualTo(START_EVENT_URL));
    }

    CallbackRequest exceptionRecordCallbackRequestWithPayment() {
        return exceptionRecordCallbackRequest(
            null,
            CASE_REF,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC,
            true
        );
    }

    CallbackRequest exceptionRecordCallbackRequest(String caseReference) {
        return exceptionRecordCallbackRequest(
            null,
            caseReference,
            CASE_TYPE_EXCEPTION_RECORD,
            EXCEPTION_RECORD_DOC,
            false
        );
    }

    CallbackRequest exceptionRecordCallbackRequest(
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId
    ) {
        return exceptionRecordCallbackRequest(
            searchCaseReferenceType,
            searchCaseReference,
            caseTypeId,
            EXCEPTION_RECORD_DOC,
            false
        );
    }

    CallbackRequest exceptionRecordCallbackRequest(
        String searchCaseReferenceType,
        String searchCaseReference,
        String caseTypeId,
        Map<String, Object> document,
        boolean containsPayment
    ) {
        return CallbackRequest
            .builder()
            .caseDetails(
                exceptionRecord(
                    null,
                    searchCaseReferenceType,
                    searchCaseReference,
                    caseTypeId,
                    document,
                    containsPayment
                )
            )
            .eventId(EVENT_ID_ATTACH_TO_CASE)
            .build();
    }

    CaseDetails exceptionRecordWith(
        String classification,
        String awaitingPaymentDcnProcessing,
        boolean includeOcr
    ) {
        Map<String, Object> caseData = exceptionRecordData(classification, includeOcr);
        caseData.put(AWAITING_PAYMENT_DCN_PROCESSING, awaitingPaymentDcnProcessing);

        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(EXCEPTION_RECORD_ID)
            .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
            .data(caseData)
            .build();
    }

    CaseDetails exceptionRecordWith(String classification, boolean includeOcr) {
        Map<String, Object> caseData = exceptionRecordData(classification, includeOcr);

        return CaseDetails.builder()
            .jurisdiction(JURISDICTION)
            .id(EXCEPTION_RECORD_ID)
            .caseTypeId(CASE_TYPE_EXCEPTION_RECORD)
            .data(caseData)
            .build();
    }

    Map<String, Object> exceptionRecordData(String classification, boolean includeOcr) {
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("journeyClassification", classification);

        if (includeOcr) {
            caseData.put("scanOCRData", singletonList(
                ImmutableMap.of("first_name", "John")
            ));
        } else {
            caseData.put("scanOCRData", emptyList());
        }

        caseData.put("scannedDocuments", ImmutableList.of(EXCEPTION_RECORD_DOC));
        caseData.put("searchCaseReference", CASE_REF);
        return caseData;
    }

    void mockSearchByLegacyIdToReturnTwoCases(
        String legacyId,
        String caseOneCcdId,
        String caseTwoCcdId
    ) throws IOException {
        mockCaseSearchByLegacyId(
            legacyId,
            okJson(
                getSearchResponseContent(
                    "ccd/response/search-by-legacy-id/result-format-two-cases.json",
                    caseOneCcdId,
                    caseTwoCcdId
                )
            )
        );
    }

    String getSearchResponseContent(
        String responseFormatResourcePath,
        String... formatArgs
    ) throws IOException {
        String formatString = Resources.toString(
            Resources.getResource(responseFormatResourcePath),
            Charset.defaultCharset()
        );

        return String.format(formatString, (Object[]) formatArgs);
    }

    private void assertMapsAreEqualIgnoringFields(
        Map<String, Object> actual,
        Map<String, Object> expected,
        String... fieldsToIgnore
    ) {
        Set<String> ignoredFieldSet = Sets.newHashSet(fieldsToIgnore);

        Set<Map.Entry<String, Object>> actualWithoutIgnoredFields = getFilteredFieldSet(actual, ignoredFieldSet);
        Set<Map.Entry<String, Object>> expectedWithoutIgnoredFields = getFilteredFieldSet(expected, ignoredFieldSet);

        assertThat(actualWithoutIgnoredFields).hasSameElementsAs(expectedWithoutIgnoredFields);
    }

    private Set<Map.Entry<String, Object>> getFilteredFieldSet(
        Map<String, Object> fieldMap,
        Set<String> fieldsToExclude
    ) {
        return fieldMap
            .entrySet()
            .stream()
            .filter(e -> !fieldsToExclude.contains(e.getKey()))
            .collect(toSet());
    }

    private static Map<String, Object> document(
        String filename,
        String documentNumber,
        Long exceptionRecordReference
    ) {

        var docUrl = ImmutableMap.of(
            "document_url", "http://localhost:3000/case/" + EXCEPTION_RECORD_DOCUMENT_UUID,
            "document_filename", filename,
            "document_binary_url", "http://localhost:3000/case/" + EXCEPTION_RECORD_DOCUMENT_UUID + "/binary"
        );
        ImmutableMap.Builder<Object, Object> doc = ImmutableMap
            .builder()
            .put("id", "e2cdab78-f299-4930-a84c-55271a97202e")
            .put("type", "other")
            .put("subtype", "OtherSupportDocuments")
            .put("fileName", filename)
            .put("controlNumber", documentNumber)
            .put("someNumber", 3)
            .put("url", docUrl);


        if (exceptionRecordReference != null) {
            doc.put("exceptionRecordReference", Long.toString(exceptionRecordReference));
        }

        return ImmutableMap.of("value", doc.build());
    }

    private static Map<String, Object> document(String filename, String documentNumber) {
        return document(filename, documentNumber, null);
    }

    private void verifyRequestPattern(RequestPatternBuilder builder, String jsonPath, StringValuePattern pattern) {
        verify(builder.withRequestBody(matchingJsonPath(jsonPath, pattern)));
    }

    Map<String, Object> exceptionDataWithDoc(
        List<Map<String, Object>> scannedDocuments,
        String attachToCaseReference,
        String searchCaseReferenceType,
        String searchCaseReference,
        boolean containsPayment
    ) {
        Map<String, Object> exceptionData = Maps.newHashMap("scannedDocuments", scannedDocuments);

        if (attachToCaseReference != null) {
            exceptionData.put("attachToCaseReference", attachToCaseReference);
        }

        if (searchCaseReferenceType != null) {
            exceptionData.put("searchCaseReferenceType", searchCaseReferenceType);
        }

        if (searchCaseReference != null) {
            exceptionData.put("searchCaseReference", searchCaseReference);
        }

        if (containsPayment) {
            exceptionData.put("containsPayments", "Yes");
            exceptionData.put(ExceptionRecordFields.ENVELOPE_ID, "21321931312-32121-312112");
            exceptionData.put(ExceptionRecordFields.PO_BOX_JURISDICTION, "sample jurisdiction");
        }

        exceptionData.put("journeyClassification", "SUPPLEMENTARY_EVIDENCE");
        return exceptionData;
    }
}
