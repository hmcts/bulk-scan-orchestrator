package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.collections4.CollectionUtils;
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
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;

@Component
public class ExceptionRecordMapper {

    private final String documentManagementUrl;
    private final String contextPath;

    public ExceptionRecordMapper(
        @Value("${document_management.url}") final String documentManagementUrl,
        @Value("${document_management.context-path}") final String contextPath
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.contextPath = contextPath;
    }

    public ExceptionRecord mapEnvelope(Envelope envelope) {
        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            envelope.formType,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            mapDocuments(envelope.documents, documentManagementUrl, contextPath, envelope.deliveryDate),
            mapOcrData(envelope.ocrData),
            mapOcrDataWarnings(envelope.ocrDataValidationWarnings),
            envelope.ocrDataValidationWarnings.isEmpty() ? NO : YES,
            envelope.id,
            CollectionUtils.isEmpty(envelope.payments) ? NO : YES,
            CollectionUtils.isEmpty(envelope.payments) ? NO : YES
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

    private List<CcdCollectionElement<String>> mapOcrDataWarnings(List<String> ocrDataWarnings) {
        return ocrDataWarnings
            .stream()
            .map(CcdCollectionElement::new)
            .collect(toList());
    }
}
