package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public class ExceptionRecord {

    @JsonProperty("journeyClassification")
    public final String classification;

    public final String poBox;

    @JsonProperty("poBoxJurisdiction")
    public final String jurisdiction;

    public final String formType;

    public final LocalDateTime deliveryDate;

    public final LocalDateTime openingDate;

    public final List<CcdCollectionElement<ScannedDocument>> scannedDocuments;

    @JsonProperty("scanOCRData")
    public final List<CcdCollectionElement<CcdKeyValue>> ocrData;

    public final List<CcdCollectionElement<String>> ocrDataValidationWarnings;

    // TODO: remove @JsonInclude when envelopeId is present in exception record definitions
    // for all services in all environments
    @JsonInclude(NON_EMPTY)
    public final String envelopeId;

    // Yes/No field indicating if there are warnings to show
    public final String displayWarnings;

    // Yes/No field indicating if the payment document control numbers are processed
    @JsonProperty("awaitingPaymentDCNProcessing")
    public final String awaitingPaymentDcnProcessing;

    // Yes/No field indicating if the exception record contains payments
    @JsonProperty("containsPayments")
    public final String containsPayments;

    // Case reference received to attach the scanned documents received
    @JsonProperty("envelopeCaseReference")
    public final String envelopeCaseReference;

    // Legacy case reference received to attach the scanned documents received
    @JsonProperty("envelopeLegacyCaseReference")
    public final String envelopeLegacyCaseReference;

    // Yes/No field indicating to show or hide envelope case reference
    @JsonProperty("showEnvelopeCaseReference")
    public final String showEnvelopeCaseReference;

    // Yes/No field indicating to show or hide envelope legacy case reference
    @JsonProperty("showEnvelopeLegacyCaseReference")
    public final String showEnvelopeLegacyCaseReference;

    @JsonProperty("surname")
    public final String surname;

    public ExceptionRecord(
        String classification,
        String poBox,
        String jurisdiction,
        String formType,
        LocalDateTime deliveryDate,
        LocalDateTime openingDate,
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments,
        List<CcdCollectionElement<CcdKeyValue>> ocrData,
        List<CcdCollectionElement<String>> ocrDataValidationWarnings,
        String displayWarnings,
        String envelopeId,
        String awaitingPaymentDcnProcessing,
        String containsPayments,
        String envelopeCaseReference,
        String envelopeLegacyCaseReference,
        String showEnvelopeCaseReference,
        String showEnvelopeLegacyCaseReference,
        String surname
    ) {
        this.classification = classification;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.formType = formType;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.scannedDocuments = scannedDocuments;
        this.ocrData = ocrData;
        this.ocrDataValidationWarnings = ocrDataValidationWarnings;
        this.displayWarnings = displayWarnings;
        this.envelopeId = envelopeId;
        this.awaitingPaymentDcnProcessing = awaitingPaymentDcnProcessing;
        this.containsPayments = containsPayments;
        this.envelopeCaseReference = envelopeCaseReference;
        this.envelopeLegacyCaseReference = envelopeLegacyCaseReference;
        this.showEnvelopeCaseReference = showEnvelopeCaseReference;
        this.showEnvelopeLegacyCaseReference = showEnvelopeLegacyCaseReference;
        this.surname = surname;
    }
}
