package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;

import java.time.Instant;
import java.util.List;

public class ExceptionRecord {

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
    public final Instant deliveryDate;

    @JsonProperty("opening_date")
    public final Instant openingDate;

    @JsonProperty("scanned_documents")
    public final List<ScannedDocument> scannedDocuments;

    @JsonProperty("ocr_data_fields")
    public final List<OcrDataField> ocrDataFields;

    public ExceptionRecord(
        String caseTypeId,
        String poBox,
        String poBoxJurisdiction,
        Classification journeyClassification,
        String formType,
        Instant deliveryDate,
        Instant openingDate,
        List<ScannedDocument> scannedDocuments,
        List<OcrDataField> ocrDataFields
    ) {
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
