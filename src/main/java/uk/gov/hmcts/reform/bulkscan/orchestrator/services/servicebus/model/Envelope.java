package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.OcrDataDeserialiser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Envelope {

    public final String id;
    public final String caseRef;
    public final String poBox;
    public final String jurisdiction;
    public final String zipFileName;
    public final Instant deliveryDate;
    public final Instant openingDate;
    public final Classification classification;
    public final List<Document> documents;
    public final Map<String, String> ocrData;

    public Envelope(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "case_ref", required = true) String caseRef,
        @JsonProperty(value = "po_box", required = true) String poBox,
        @JsonProperty(value = "jurisdiction", required = true) String jurisdiction,
        @JsonProperty(value = "zip_file_name", required = true) String zipFileName,
        @JsonProperty(value = "delivery_date", required = true) Instant deliveryDate,
        @JsonProperty(value = "opening_date", required = true) Instant openingDate,
        @JsonProperty(value = "classification", required = true) Classification classification,
        @JsonProperty(value = "documents", required = true) List<Document> documents,
        @JsonDeserialize(using = OcrDataDeserialiser.class)
        @JsonProperty(value = "ocr_data") Map<String, String> ocrData
    ) {
        this.id = id;
        this.caseRef = caseRef;
        this.poBox = poBox;
        this.jurisdiction = jurisdiction;
        this.zipFileName = zipFileName;
        this.deliveryDate = deliveryDate;
        this.openingDate = openingDate;
        this.classification = classification;
        this.documents = documents;
        this.ocrData = ocrData;
    }

    public void addDocuments(List<Document> documents) {
        this.documents.addAll(documents);
    }
}
