package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;

import java.time.LocalDateTime;
import java.util.List;

public class CaseUpdateDetails {

    @JsonPropertyDescription("Case Type ID of the exception record."
        + " Present only when the case update is based on exception record")
    @JsonProperty("exception_record_case_type_id")
    public final String exceptionRecordCaseTypeId;

    @JsonPropertyDescription("Exception Record ID. Present only when the case update is based on exception record")
    @JsonProperty("exception_record_id")
    public final String exceptionRecordId;

    @JsonPropertyDescription("ID of Bulk Scan envelope which is the source of form data contained in this request")
    @JsonProperty(value = "envelope_id", required = true)
    public final String envelopeId;

    @JsonProperty(value = "po_box", required = true)
    public final String poBox;

    @JsonProperty(value = "po_box_jurisdiction", required = true)
    public final String poBoxJurisdiction;

    @JsonProperty(value = "form_type", required = true)
    public final String formType;

    @JsonProperty(value = "delivery_date", required = true)
    public final LocalDateTime deliveryDate;

    @JsonProperty(value = "opening_date", required = true)
    public final LocalDateTime openingDate;

    @JsonProperty(value = "scanned_documents", required = true)
    public final List<ScannedDocument> scannedDocuments;

    @JsonProperty(value = "ocr_data_fields", required = true)
    public final List<OcrDataField> ocrDataFields;

    // TODO: remove this constructor when the switch to the other one is done
    public CaseUpdateDetails(ExceptionRecord exceptionRecord, String envelopeId) {
        this.exceptionRecordCaseTypeId = exceptionRecord.caseTypeId;
        this.exceptionRecordId = exceptionRecord.id;
        this.envelopeId = envelopeId;
        this.poBox = exceptionRecord.poBox;
        this.poBoxJurisdiction = exceptionRecord.poBoxJurisdiction;
        this.formType = exceptionRecord.formType;
        this.deliveryDate = exceptionRecord.deliveryDate;
        this.openingDate = exceptionRecord.openingDate;
        this.scannedDocuments = exceptionRecord.scannedDocuments;
        this.ocrDataFields = exceptionRecord.ocrDataFields;
    }

    public CaseUpdateDetails(
        String exceptionRecordCaseTypeId,
        String exceptionRecordId,
        String envelopeId,
        String poBox,
        String poBoxJurisdiction,
        String formType,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<ScannedDocument> scannedDocuments,
        List<OcrDataField> ocrDataFields
    ) {
        this.exceptionRecordCaseTypeId = exceptionRecordCaseTypeId;
        this.exceptionRecordId = exceptionRecordId;
        this.envelopeId = envelopeId;
        this.poBox = poBox;
        this.poBoxJurisdiction = poBoxJurisdiction;
        this.formType = formType;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrDataFields = ocrDataFields;
    }
}
