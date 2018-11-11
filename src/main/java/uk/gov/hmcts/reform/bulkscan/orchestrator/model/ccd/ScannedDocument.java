package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import java.time.LocalDateTime;
import java.util.Objects;

public class ScannedDocument {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final LocalDateTime scannedDate;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ScannedDocument that = (ScannedDocument) o;
        return Objects.equals(fileName, that.fileName)
            && Objects.equals(controlNumber, that.controlNumber)
            && Objects.equals(type, that.type)
            && Objects.equals(scannedDate, that.scannedDate)
            && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, controlNumber, type, scannedDate, url);
    }
}
