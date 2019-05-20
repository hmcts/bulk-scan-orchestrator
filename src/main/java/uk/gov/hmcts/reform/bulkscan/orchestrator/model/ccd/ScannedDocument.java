package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScannedDocument {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final String subtype;
    public final String exceptionReference;
    public final LocalDateTime scannedDate;
    public final CcdDocument url;
    public final String uuid;

    public ScannedDocument(
        @JsonProperty("fileName") String fileName,
        @JsonProperty("controlNumber") String controlNumber,
        @JsonProperty("type") String type,
        @JsonProperty("subtype") String subtype,
        @JsonProperty("scannedDate") LocalDateTime scannedDate,
        @JsonProperty("url") CcdDocument url,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("exceptionRecordReference") String exceptionReference
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.subtype = subtype;
        this.scannedDate = scannedDate;
        this.url = url;
        this.uuid = uuid;
        this.exceptionReference = exceptionReference;
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
            && Objects.equals(subtype, that.subtype)
            && Objects.equals(scannedDate, that.scannedDate)
            && Objects.equals(uuid, that.uuid)
            && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, controlNumber, type, scannedDate, url, uuid);
    }
}
