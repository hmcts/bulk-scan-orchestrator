package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.model.caserecord.SupplementaryEvidenceRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CaseRecordFactoryTest {

    private static final String ENVELOPE_ID = "id";
    private static final String ENVELOPE_CASE_REF = "caseRef";
    private static final String ENVELOPE_JURISDICTION = "jurisdiction";
    private static final String ENVELOPE_ZIP_FILE_NAME = "zip";

    @Test
    public void should_get_supplementary_evidence_record() {
        // given
        Envelope envelope = getDummyEnvelope(Classification.SUPPLEMENTARY_EVIDENCE);

        // when
        CaseDataCreator creator = CaseRecordFactory.getCaseDataCreator(envelope, mock(CaseDetails.class));

        // then
        assertThat(creator)
            .isInstanceOf(SupplementaryEvidenceRecord.class)
            .extracting("caseRecordIdentifier")
            .containsOnly(CaseRecord.Record.SUPPLEMENTARY_EVIDENCE);
    }

    @Test
    public void should_get_exception_record_when_case_is_empty() {
        // given
        Envelope envelope = getDummyEnvelope(Classification.SUPPLEMENTARY_EVIDENCE);

        // when
        CaseDataCreator creator = CaseRecordFactory.getCaseDataCreator(envelope, null);

        // then
        assertThat(creator)
            .isInstanceOf(ExceptionRecord.class)
            .extracting("caseRecordIdentifier")
            .containsOnly(CaseRecord.Record.EXCEPTION_RECORD);
    }

    // can be separated once cleared up about classifications and case creation in general
    @Test
    public void should_get_exception_record() {
        // given
        Envelope envelope1 = getDummyEnvelope(Classification.EXCEPTION);
        Envelope envelope2 = getDummyEnvelope(Classification.NEW_APPLICATION);

        // when
        CaseDataCreator creator1 = CaseRecordFactory.getCaseDataCreator(envelope1, null);
        CaseDataCreator creator2 = CaseRecordFactory.getCaseDataCreator(envelope2, null);

        // then
        assertThat(creator1)
            .isInstanceOf(ExceptionRecord.class)
            .extracting("caseRecordIdentifier")
            .containsOnly(CaseRecord.Record.EXCEPTION_RECORD);
        assertThat(creator2)
            .isInstanceOf(ExceptionRecord.class)
            .extracting("caseRecordIdentifier")
            .containsOnly(CaseRecord.Record.EXCEPTION_RECORD);
    }

    private Envelope getDummyEnvelope(Classification classification) {
        return new Envelope(
            ENVELOPE_ID,
            ENVELOPE_CASE_REF,
            ENVELOPE_JURISDICTION,
            ENVELOPE_ZIP_FILE_NAME,
            classification,
            emptyList()
        );
    }
}
