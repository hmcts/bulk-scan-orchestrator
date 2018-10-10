package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.CaseDataCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.ccd.client.model.Classification.PRIVATE;

// todo placeholder to update unit test once record is implemented
public class SupplementaryEvidenceRecordTest {

    @Test
    public void should_create_case_data_content() {
        // given
        Envelope envelope = new Envelope(
            "id",
            "caseRef",
            "jurisdiction",
            "zip",
            Classification.SUPPLEMENTARY_EVIDENCE,
            Collections.emptyList()
        );
        CaseDataCreator creator = new SupplementaryEvidenceRecord(envelope, null);

        // when
        CaseDataContent dataContent = creator.createDataContent(PRIVATE, "eventToken", true);

        // then
        assertThat(dataContent).isNull();
    }
}
