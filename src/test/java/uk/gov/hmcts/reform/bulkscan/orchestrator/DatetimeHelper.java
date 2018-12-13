package uk.gov.hmcts.reform.bulkscan.orchestrator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.format.DateTimeFormatter.ofPattern;

public class DatetimeHelper {

    public static String toIso8601(Instant instant) {
        return ZonedDateTime
            .ofInstant(instant, ZoneId.systemDefault())
            .format(ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }

    private DatetimeHelper() {
        // util class
    }
}
