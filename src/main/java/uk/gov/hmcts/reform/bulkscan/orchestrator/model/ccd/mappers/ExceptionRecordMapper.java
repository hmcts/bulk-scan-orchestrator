package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentsMapper.getLocalDateTime;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentsMapper.mapDocuments;

public class ExceptionRecordMapper {

    private ExceptionRecordMapper() {
        // util class
    }

    public static ExceptionRecord mapEnvelope(Envelope envelope) {
        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            mapDocuments(envelope.documents),
            mapOcrData(envelope.ocrData)
        );
    }

    private static List<CcdCollectionElement<CcdKeyValue>> mapOcrData(Map<String, String> ocrData) {
        if (ocrData != null) {
            return ocrData
                .entrySet()
                .stream()
                .map(entry -> new CcdKeyValue(entry.getKey(), entry.getValue()))
                .map(CcdCollectionElement::new)
                .collect(toList());
        } else {
            return null;
        }
    }
}
