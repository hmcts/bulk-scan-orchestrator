package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTest {

    @Test
    void should_get_uuid() {
        assertThat(Util.getDocumentUuid("http://localhost/uuid1")).isEqualTo("uuid1");
    }
}
