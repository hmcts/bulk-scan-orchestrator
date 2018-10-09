package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

public class ScannedDocument {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final LocalDate scannedDate;

    // TODO: remove @JsonIgnore annotation once the url is solved
    @JsonIgnore
    public final CCDDocument url;

    public ScannedDocument(
        String fileName,
        String controlNumber,
        String type,
        LocalDate scannedDate,
        CCDDocument url
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.scannedDate = scannedDate;
        this.url = url;
    }
}
