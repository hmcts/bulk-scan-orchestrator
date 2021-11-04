package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest {

    @Test
    void should_get_uuid() {
        assertThat(Util.getDocumentUuid("http://localhost/uuid1")).isEqualTo("uuid1");
    }

    @Test
    void should_get_local_date_time() {
        assertThat(Util.getLocalDateTime(null)).isNull();

        Instant instant = ZonedDateTime.parse("2020-04-01T12:34:56.00Z").toInstant();
        assertThat(Util.getLocalDateTime(instant))
                .isEqualTo(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
}
