package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.Arrays.asList;

public class SampleData {

    private SampleData() {
        // hiding the constructor
    }

    public static CaseDetails sampleCaseDetails() {
        return CaseDetails
            .builder()
            .id(123L)
            .caseTypeId("case_caseTypeId1")
            .data(ImmutableMap.of("field1", "value1"))
            .build();
    }

    public static ExceptionRecord sampleExceptionRecord() {
        return new ExceptionRecord(
            "id1",
            "er_caseTypeId1",
            "envelopeId1",
            "poBox1",
            "poBoxJurisdiction1",
            Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "formType1",
            LocalDateTime.now(),
            LocalDateTime.now().plusSeconds(1),
            sampleScannedDocuments(),
            asList(
                new OcrDataField("name1", "value1"),
                new OcrDataField("name2", "value2")
            )
        );
    }

    private static List<ScannedDocument> sampleScannedDocuments() {
        return asList(
            new ScannedDocument(
                DocumentType.FORM,
                "subtype1",
                new DocumentUrl("url1", "binaryUrl1", "filename1"),
                "controlNumber1",
                "filename1",
                LocalDateTime.now().plusSeconds(2),
                LocalDateTime.now().plusSeconds(3)
            ),
            new ScannedDocument(
                DocumentType.OTHER,
                "subtype2",
                new DocumentUrl("url2", "binaryUrl2", "filename2"),
                "controlNumber2",
                "filename2",
                LocalDateTime.now().plusSeconds(22),
                LocalDateTime.now().plusSeconds(33)
            )
        );
    }
}
