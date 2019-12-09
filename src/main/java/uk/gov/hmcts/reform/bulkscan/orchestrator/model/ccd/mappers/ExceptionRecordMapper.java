package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.getLocalDateTime;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@Component
public class ExceptionRecordMapper {

    private final String documentManagementUrl;
    private final String contextPath;
    private final ServiceConfigProvider serviceConfigProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionRecordMapper.class);

    // Display Envelope case references for the specified classifications
    private static final EnumSet<Classification> ALLOWED_CLASSIFICATIONS = EnumSet.of(
        SUPPLEMENTARY_EVIDENCE,
        SUPPLEMENTARY_EVIDENCE_WITH_OCR
    );

    public ExceptionRecordMapper(
        @Value("${document_management.url}") final String documentManagementUrl,
        @Value("${document_management.context-path}") final String contextPath,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.contextPath = contextPath;
        this.serviceConfigProvider = serviceConfigProvider;
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
            CollectionUtils.isEmpty(envelope.payments) ? NO : YES,
            // Setting the values as "", as null values are not supported by ImmutableMap
            isBlank(envelope.caseRef) ? "" : envelope.caseRef,
            isBlank(envelope.legacyCaseRef) ? "" : envelope.legacyCaseRef,
            setDisplayCaseReferenceFlag(envelope.caseRef, envelope.classification),
            setDisplayCaseReferenceFlag(envelope.legacyCaseRef, envelope.classification),
            extractSurnameFromOcrData(envelope)
        );
    }

    private String setDisplayCaseReferenceFlag(String caseRef, Classification classification) {
        if (isNotBlank(caseRef) && ALLOWED_CLASSIFICATIONS.contains(classification)) {
            return YES;
        }
        return NO;
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

    private String extractSurnameFromOcrData(Envelope envelope) {
        if (CollectionUtils.isEmpty(envelope.ocrData)) {
            return null;
        }

        String surnameOcrFieldName = serviceConfigProvider.getConfig(envelope.container)
            .getSurnameOcrFieldName(envelope.formType);

        List<String> surnameList = envelope.ocrData.stream().filter(ocrData -> ocrData.name.equals(surnameOcrFieldName))
            .map(ocrData -> ocrData.value).collect(Collectors.toList());
        if (surnameList.size() == 0) {
            LOGGER.info(
                "Surname not found in OCR data. Surname Ocr Field Name:{}. Envelope id:{},Case Ref:{},Jurisdiction:{}",
                surnameOcrFieldName,
                envelope.id,
                envelope.caseRef,
                envelope.jurisdiction
            );
            return null;
        } else if (surnameList.size() > 1) {
            LOGGER.info(
                "Surname found {} times in OCR data."
                    + "Surname Ocr Field Name:{} Envelope id:{},Case Ref:{},Jurisdiction:{}",
                surnameList.size(),
                surnameOcrFieldName,
                envelope.id,
                envelope.caseRef,
                envelope.jurisdiction
            );
        }
        LOGGER.info("Surname found {} ", surnameList.get(0));

        return surnameList.get(0);
    }
}
