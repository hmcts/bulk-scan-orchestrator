package uk.gov.hmcts.reform.bulkscan.orchestrator.feature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCollectionElementComparator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class OcrDataOrderTest {

    private ServiceConfigProvider serviceConfigProvider = mock(ServiceConfigProvider.class);
    private ServiceConfigItem serviceConfigItem = mock(ServiceConfigItem.class);

    @DisplayName("Should parse incoming envelope with OCR data and map to same order in CCD record")
    @Test
    public void sameOrder() {
        // given
        byte[] envelopeMessage = SampleData.exampleJsonAsBytes();
        given(serviceConfigProvider.getConfig("container")).willReturn(serviceConfigItem);
        given(serviceConfigItem.getSurnameOcrFieldNameList(any())).willReturn(Optional.of(asList("field_surname")));

        // when
        Envelope envelope = EnvelopeParser.parse(envelopeMessage);
        assertThat(envelope.ocrData).isInstanceOf(ArrayList.class);

        // and
        ExceptionRecordMapper mapper = new ExceptionRecordMapper(
            "http://localhost",
            "files",
            serviceConfigProvider
        );

        ExceptionRecord record = mapper.mapEnvelope(envelope);
        assertThat(record.ocrData).isInstanceOf(ArrayList.class);
        assertThat(record.ocrData.size()).isEqualTo(envelope.ocrData.size());

        // then
        int i = 0;

        ListIterator<OcrDataField> entries = envelope.ocrData.listIterator();
        while (entries.hasNext()) {
            OcrDataField expectedEntry = entries.next();
            assertThat(record.ocrData.get(i).value.key).isEqualTo(expectedEntry.name);
            assertThat(record.ocrData.get(i).value.value).isEqualTo(expectedEntry.value);
            i++;
        }

        // and (just for sanity/clearance)
        assertThat(record.ocrData)
            .usingElementComparator(new CcdCollectionElementComparator())
            .containsExactly(
                new CcdCollectionElement<>(new CcdKeyValue("text_field", "some text")),
                new CcdCollectionElement<>(new CcdKeyValue("number_field", "123")),
                new CcdCollectionElement<>(new CcdKeyValue("boolean_field", "true")),
                new CcdCollectionElement<>(new CcdKeyValue("null_field", null))
            );

        assertThat(record.ocrDataValidationWarnings)
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactly(
                new CcdCollectionElement<>("warning 1")
            );
    }
}
