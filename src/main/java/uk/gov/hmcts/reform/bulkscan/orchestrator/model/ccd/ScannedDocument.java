package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public class ScannedDocument {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final LocalDateTime scannedDate;

    // TODO: remove @JsonIgnore annotation once the url problem is solved in local env
    @JsonIgnore
    public final CcdDocument url;

    public ScannedDocument(
        String fileName,
        String controlNumber,
        String type,
        LocalDateTime scannedDate,
        CcdDocument url
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.scannedDate = scannedDate;
        this.url = url;
    }
}
