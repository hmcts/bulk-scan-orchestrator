package uk.gov.hmcts.reform.bulkscan.orchestrator.feature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCollectionElementComparator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OcrDataOrderTest {

    @DisplayName("Should parse incoming envelope with OCR data and map to same order in CCD record")
    @Test
    public void sameOrder() {
        // given
        byte[] envelopeMessage = SampleData.exampleJsonAsBytes();

        // when
        Envelope envelope = EnvelopeParser.parse(envelopeMessage);
        assertThat(envelope.ocrData).isInstanceOf(LinkedHashMap.class);

        // and
        ExceptionRecord record = ExceptionRecordMapper.mapEnvelope(envelope);
        assertThat(record.ocrData).isInstanceOf(ArrayList.class);
        assertThat(record.ocrData.size()).isEqualTo(envelope.ocrData.size());

        // then
        int i = 0;
        Iterator<Map.Entry<String, String>> entries = envelope.ocrData.entrySet().iterator();

        while (entries.hasNext()) {
            Map.Entry<String, String> expectedEntry = entries.next();
            assertThat(record.ocrData.get(i).value.key).isEqualTo(expectedEntry.getKey());
            assertThat(record.ocrData.get(i).value.value).isEqualTo(expectedEntry.getValue());
            i++;
        }

        // and (just for sanity/clearance)
        assertThat(record.ocrData)
            .usingElementComparator(new CcdCollectionElementComparator())
            .containsExactly(
                new CcdCollectionElement<>(new CcdKeyValue("text_field", "some text")),
                new CcdCollectionElement<>(new CcdKeyValue("number_field", "123")),
                new CcdCollectionElement<>(new CcdKeyValue("boolean_field", "true")),
                new CcdCollectionElement<>(new CcdKeyValue("null_field", ""))
            );
    }
}
