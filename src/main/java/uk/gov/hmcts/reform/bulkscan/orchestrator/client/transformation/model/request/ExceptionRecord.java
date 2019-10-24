package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.time.LocalDateTime;
import java.util.List;

public class ExceptionRecord {

    public final String id;

    @JsonProperty("case_type_id")
    public final String caseTypeId;

    @JsonProperty("po_box")
    public final String poBox;

    @JsonProperty("po_box_jurisdiction")
    public final String poBoxJurisdiction;

    @JsonProperty("journey_classification")
    public final Classification journeyClassification;

    @JsonProperty("form_type")
    public final String formType;

    @JsonProperty("delivery_date")
    public final LocalDateTime deliveryDate;

    @JsonProperty("opening_date")
    public final LocalDateTime openingDate;

    @JsonProperty("scanned_documents")
    public final List<ScannedDocument> scannedDocuments;

    @JsonProperty("ocr_data_fields")
    public final List<OcrDataField> ocrDataFields;

    public ExceptionRecord(
        String id,
        String caseTypeId,
        String poBox,
        String poBoxJurisdiction,
        Classification journeyClassification,
        String formType,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<ScannedDocument> scannedDocuments,
        List<OcrDataField> ocrDataFields
    ) {
        this.id = id;
        this.caseTypeId = caseTypeId;
        this.poBox = poBox;
        this.poBoxJurisdiction = poBoxJurisdiction;
        this.journeyClassification = journeyClassification;
        this.formType = formType;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrDataFields = ocrDataFields;
    }
}
