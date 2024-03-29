package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response.CaseUpdateDetails;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EnvelopeReferenceHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.fileContentAsBytes;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleEnvelope;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType.FORM;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ServiceCaseFields.BULK_SCAN_ENVELOPES;

@SuppressWarnings({"unchecked", "checkstyle:LineLength"})
@ExtendWith(MockitoExtension.class)
class CaseDataUpdaterTest {

    @Mock EnvelopeReferenceHelper envelopeReferenceHelper;
    @Mock CdamApiClient cdamApiClient;


    CaseDataUpdater caseDataUpdater;

    @BeforeEach
    void setUp() {
        this.caseDataUpdater = new CaseDataUpdater(envelopeReferenceHelper, cdamApiClient);
    }

    @Test
    void sets_exception_record_id_and_hashToken_to_scanned_documents() throws Exception {
        // given
        var jurisdiction = "poBoxJurisdiction";
        var serviceConfigItem = new ServiceConfigItem();
        serviceConfigItem.setJurisdiction(jurisdiction);

        var hashToken1 = "321hhjRETE31321dsds";
        var hashToken2 = "321hhjRETE31321dsds";
        given(cdamApiClient.getDocumentHash(jurisdiction, "uuid1"))
            .willReturn(hashToken1);
        given(cdamApiClient.getDocumentHash(jurisdiction, "uuid2"))
            .willReturn(hashToken2);
        var caseDetails = getCaseUpdateDetails("case-data/multiple-scanned-docs.json");
        // case contains documents with control numbers 1000, 2000, 3000
        var scannedDocuments = asList(
            getScannedDocument("1000"),
            getScannedDocument("2000")
        );
        var exceptionRecord = new ExceptionRecord(
            "123456789",
            "caseTypeId",
            "envelopeId123",
            "poBox",
            "poBoxJurisdiction",
            Classification.EXCEPTION,
            "formType",
            now(),
            now(),
            scannedDocuments,
            emptyList()
        );
        // when
        var updatedCaseData = caseDataUpdater.setExceptionRecordIdAndHashTokenToScannedDocuments(
            exceptionRecord,
            caseDetails.caseData
        );

        //then
        var updatedScannedDocuments = getScannedDocuments(updatedCaseData);
        assertThat(updatedScannedDocuments).hasSize(3);
        assertThat(updatedScannedDocuments.get(0).controlNumber).isEqualTo("1000");
        assertThat(updatedScannedDocuments.get(0).exceptionReference).isEqualTo(exceptionRecord.id);
        assertThat(updatedScannedDocuments.get(0).url.documentHash).isEqualTo(hashToken1);
        assertThat(updatedScannedDocuments.get(0).url.documentUrl).isEqualTo("https://doc-url-1.example.com/uuid1");
        assertThat(updatedScannedDocuments.get(1).controlNumber).isEqualTo("2000");
        assertThat(updatedScannedDocuments.get(1).exceptionReference).isEqualTo(exceptionRecord.id);
        assertThat(updatedScannedDocuments.get(1).url.documentHash).isEqualTo(hashToken2);
        assertThat(updatedScannedDocuments.get(1).url.documentUrl).isEqualTo("https://doc-url-2.example.com/uuid2");

        // not present in the exception record and should not be set
        assertThat(updatedScannedDocuments.get(2).controlNumber).isEqualTo("3000");
        assertThat(updatedScannedDocuments.get(2).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(2).url.documentHash).isNull();
        assertThat(updatedScannedDocuments.get(2).url.documentUrl).isEqualTo("https://doc-url-3.example.com");


    }

    @Test
    void sets_document_hashToken_to_scanned_documents_for_envelope() throws Exception {

        String docUuid1 = UUID.randomUUID().toString();
        String docUuid2 = UUID.randomUUID().toString();
        var document1 = new Document(
            "evidence1.pdf",
            "1000",
            "other",
            null,
            Instant.now(),
            docUuid1,
            Instant.now()
        );
        var document2 = new Document(
            "evidence2.pdf",
            "2000",
            "PB1",
            null,
            Instant.now(),
            docUuid2,
            Instant.now()
        );

        var hashToken1 = "321hhjRETE31321dsds";
        var hashToken2 = "321hhjRETE31321dsds";
        given(cdamApiClient.getDocumentHash("jurisdiction1", docUuid1))
            .willReturn(hashToken1);
        given(cdamApiClient.getDocumentHash("jurisdiction1", docUuid2))
            .willReturn(hashToken2);

        Envelope envelope = sampleEnvelope(List.of(), List.of(document1, document2));
        var caseDetails = getCaseUpdateDetails("case-data/multiple-scanned-docs.json");

        var updatedCaseData = caseDataUpdater.setDocumentHash(
            envelope,
            caseDetails.caseData
        );

        var updatedScannedDocuments = getScannedDocuments(updatedCaseData);

        assertThat(updatedScannedDocuments).hasSize(3);
        assertThat(updatedScannedDocuments.get(0).controlNumber).isEqualTo("1000");
        assertThat(updatedScannedDocuments.get(0).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(0).url.documentHash).isEqualTo(hashToken1);
        assertThat(updatedScannedDocuments.get(0).url.documentUrl).isEqualTo("https://doc-url-1.example.com/uuid1");
        assertThat(updatedScannedDocuments.get(1).controlNumber).isEqualTo("2000");
        assertThat(updatedScannedDocuments.get(1).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(1).url.documentHash).isEqualTo(hashToken2);
        assertThat(updatedScannedDocuments.get(1).url.documentUrl).isEqualTo("https://doc-url-2.example.com/uuid2");

        // not present in the envelope and should not be set
        assertThat(updatedScannedDocuments.get(2).controlNumber).isEqualTo("3000");
        assertThat(updatedScannedDocuments.get(2).exceptionReference).isNull();
        assertThat(updatedScannedDocuments.get(2).url.documentHash).isNull();
        assertThat(updatedScannedDocuments.get(2).url.documentUrl).isEqualTo("https://doc-url-3.example.com");
    }

    @Test
    void should_update_exception_record_references() throws Exception {
        //given
        var existingCase = getCaseUpdateDetails("case-data/envelope-refs/with-refs.json");
        var caseDataBeforeUpdate = existingCase.caseData;

        Map<String, Object> transformedCaseData = Map.of("K_1", "value_x");
        given(envelopeReferenceHelper.parseEnvelopeReferences(any()))
            .willReturn(
                List.of(
                    new CcdCollectionElement<>(new EnvelopeReference("OLD-id", CaseAction.CREATE))
                )
            );

        // when
        var caseDataAfterUpdate =
            caseDataUpdater.updateEnvelopeReferences(transformedCaseData, "NEW-id", CaseAction.UPDATE, caseDataBeforeUpdate);

        // then
        var refsAfterUpdate = (List<CcdCollectionElement<EnvelopeReference>>) caseDataAfterUpdate.get(BULK_SCAN_ENVELOPES);
        assertThat(refsAfterUpdate.stream().map(ccdElement -> ccdElement.value))
            .usingFieldByFieldElementComparator()
            .containsExactlyInAnyOrder(
                new EnvelopeReference("OLD-id", CaseAction.CREATE),
                new EnvelopeReference("NEW-id", CaseAction.UPDATE)
            );

        assertThat(caseDataAfterUpdate.entrySet().stream().filter(e -> e.getKey().equals(BULK_SCAN_ENVELOPES)).collect(toList()))
            .as("All other fields should remain unchanged")
            .isEqualTo(caseDataAfterUpdate.entrySet().stream().filter(e -> e.getKey().equals(BULK_SCAN_ENVELOPES)).collect(toList()));
    }

    private CaseUpdateDetails getCaseUpdateDetails(String resourceName) throws IOException {
        return objectMapper.readValue(fileContentAsBytes(resourceName), CaseUpdateDetails.class);
    }

    private List<ScannedDocument> getScannedDocuments(Map<String, Object> caseData) {
        return ((List<Map<String, Object>>) caseData.get(SCANNED_DOCUMENTS))
            .stream()
            .map(o -> objectMapper.convertValue(o.get("value"), ScannedDocument.class))
            .collect(toList());
    }

    private uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument getScannedDocument(String controlNumber) {
        return new uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument(
            FORM,
            "subtype",
            new DocumentUrl("url", "binaryUrl", "fileName"),
            controlNumber,
            "fileName",
            now(),
            now()
        );
    }

}
