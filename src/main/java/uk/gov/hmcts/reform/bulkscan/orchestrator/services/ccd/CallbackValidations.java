package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vavr.control.Validation.invalid;

public final class CallbackValidations {

    // todo review usage
    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        // date/time
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        // optional offset
        .optionalStart().appendOffsetId()
        .toFormatter()
        .withZone(ZoneOffset.UTC);

    private CallbackValidations() {
    }

    public static Validation<String, LocalDateTime> hasDateField(CaseDetails theCase, String dateField) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(dateField))
            .map(o -> Validation.<String, LocalDateTime>valid(LocalDateTime.parse((String) o, FORMATTER)))
            .orElse(invalid("Missing " + dateField));
    }

    @SuppressWarnings("unchecked")
    public static Optional<List<Map<String, Object>>> getOcrData(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> (List<Map<String, Object>>) data.get("scanOCRData"));
    }
}
