package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.OcrDataField;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.getLocalDateTime;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;

@Component
public class ExceptionRecordMapper {

    private String documentManagementApiUrl;

    public ExceptionRecordMapper(@Value("${document_management.url}") final String documentManagementApiUrl) {
        this.documentManagementApiUrl = documentManagementApiUrl;
    }

    public ExceptionRecord mapEnvelope(Envelope envelope) {
        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            mapDocuments(envelope.documents, documentManagementApiUrl),
            mapOcrData(envelope.ocrData)
        );
    }

    private List<CcdCollectionElement<CcdKeyValue>> mapOcrData(List<OcrDataField> ocrData) {
        if (ocrData != null) {
            return ocrData
                .stream()
                .map(ocrDataField -> new CcdKeyValue(ocrDataField.name, ocrDataField.value))
                .map(CcdCollectionElement::new)
                .collect(toList());
        }
        return null;
    }
}
