package uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal;

import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Internal representation of exception record.
 */
public class ExceptionRecord {

    public final String id;
    public final String caseTypeId;
    public final String envelopeId;
    public final String poBox;
    public final String poBoxJurisdiction;
    public final Classification journeyClassification;
    public final String formType;
    public final LocalDateTime deliveryDate;
    public final LocalDateTime openingDate;
    public final List<ScannedDocument> scannedDocuments;
    public final List<OcrDataField> ocrDataFields;

    public ExceptionRecord(
        String id,
        String caseTypeId,
        String envelopeId,
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
        this.envelopeId = envelopeId;
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
