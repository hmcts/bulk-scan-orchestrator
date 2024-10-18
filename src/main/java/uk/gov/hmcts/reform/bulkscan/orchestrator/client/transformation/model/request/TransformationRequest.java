package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.time.LocalDateTime;
import java.util.List;

public class TransformationRequest {

    /**
     * Deprecated. Use {@link #exceptionRecordId} instead.
     */
    @Deprecated
    public final String id;

    @JsonProperty("exception_record_id")
    public final String exceptionRecordId;

    /**
     * Deprecated. Use {@link #exceptionRecordCaseTypeId} instead.
     */
    @Deprecated
    @JsonProperty("case_type_id")
    public final String caseTypeId;

    @JsonProperty("exception_record_case_type_id")
    public final String exceptionRecordCaseTypeId;

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("is_automated_process")
    public final boolean isAutomatedProcess;

    @JsonProperty("po_box")
    public final String poBox;

    @JsonProperty("po_box_jurisdiction")
    public final String poBoxJurisdiction;

    @JsonProperty("journey_classification")
    public final Classification journeyClassification;

    @JsonProperty("form_type")
    public final String formType;

    @JsonProperty("delivery_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public final LocalDateTime deliveryDate;

    @JsonProperty("opening_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    public final LocalDateTime openingDate;

    @JsonProperty("scanned_documents")
    public final List<ScannedDocument> scannedDocuments;

    @JsonProperty("ocr_data_fields")
    public final List<OcrDataField> ocrDataFields;

    @JsonProperty("ignore_warnings")
    public final boolean ignoreWarnings;

    public TransformationRequest(
        String id,
        String caseTypeId,
        String envelopeId,
        boolean isAutomatedProcess,
        String poBox,
        String poBoxJurisdiction,
        Classification journeyClassification,
        String formType,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<ScannedDocument> scannedDocuments,
        List<OcrDataField> ocrDataFields,
        boolean ignoreWarnings
    ) {
        this.id = id;
        this.exceptionRecordId = id;
        this.caseTypeId = caseTypeId;
        this.exceptionRecordCaseTypeId = caseTypeId;
        this.envelopeId = envelopeId;
        this.isAutomatedProcess = isAutomatedProcess;
        this.poBox = poBox;
        this.poBoxJurisdiction = poBoxJurisdiction;
        this.journeyClassification = journeyClassification;
        this.formType = formType;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrDataFields = ocrDataFields;
        this.ignoreWarnings = ignoreWarnings;
    }
}
