package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class Util {
    private Util() {
        // utility class
    }

    public static String getDocumentUuid(String documentUrl) {
        return documentUrl.substring(documentUrl.lastIndexOf("/") + 1);
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
