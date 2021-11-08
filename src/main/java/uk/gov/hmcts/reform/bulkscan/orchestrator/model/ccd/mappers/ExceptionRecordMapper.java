package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;

import java.util.EnumSet;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.NO;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.getLocalDateTime;

@Component
public class ExceptionRecordMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionRecordMapper.class);

    // Display Envelope case references for the specified classifications
    private static final EnumSet<Classification> ALLOWED_CLASSIFICATIONS = EnumSet.of(
        SUPPLEMENTARY_EVIDENCE,
        SUPPLEMENTARY_EVIDENCE_WITH_OCR
    );

    private final ServiceConfigProvider serviceConfigProvider;
    private final DocMapper docMapper;

    public ExceptionRecordMapper(
            ServiceConfigProvider serviceConfigProvider,
            DocMapper docMapper
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.docMapper = docMapper;
    }

    public ExceptionRecord mapEnvelope(Envelope envelope) {
        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            envelope.formType,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            docMapper.mapDocuments(List.of(), envelope.documents, envelope.deliveryDate, envelope.jurisdiction),
            mapOcrData(envelope.ocrData),
            mapOcrDataWarnings(envelope.ocrDataValidationWarnings),
            envelope.ocrDataValidationWarnings.isEmpty() ? NO : YES,
            envelope.id,
            CollectionUtils.isEmpty(envelope.payments) ? NO : YES,
            CollectionUtils.isEmpty(envelope.payments) ? NO : YES,
            // Setting the values as "", as null values are not supported by ImmutableMap
            isNullOrEmpty(envelope.caseRef) ? "" : envelope.caseRef,
            isNullOrEmpty(envelope.legacyCaseRef) ? "" : envelope.legacyCaseRef,
            setDisplayCaseReferenceFlag(envelope.caseRef, envelope.classification),
            setDisplayCaseReferenceFlag(envelope.legacyCaseRef, envelope.classification),
            extractSurnameFromOcrData(envelope)
        );
    }

    private String setDisplayCaseReferenceFlag(String caseRef, Classification classification) {
        if (!isNullOrEmpty(caseRef) && ALLOWED_CLASSIFICATIONS.contains(classification)) {
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
        LOGGER.info(
            "Extracting surname from ocr data, Envelope id: {}, Container: {} Form type: {}",
            envelope.id,
            envelope.container,
            envelope.formType
        );

        if (CollectionUtils.isEmpty(envelope.ocrData)) {
            LOGGER.info("There is no ocr data to extract surname, Envelope id: {}", envelope.id);
            return null;
        }

        List<String> surnameOcrFieldNameList =
            serviceConfigProvider
                .getConfig(envelope.container)
                .getSurnameOcrFieldNameList(envelope.formType);


        if (surnameOcrFieldNameList.isEmpty()) {
            LOGGER.info("Ocr surname field name config empty, Envelope id: {} ", envelope.id);
            return null;
        }

        for (String surnameOcrFieldName : surnameOcrFieldNameList) {

            LOGGER.info(
                "For Envelope id: {}, Searching surname by ocr field name: {}",
                envelope.id,
                surnameOcrFieldName
            );

            List<String> surnameList = envelope.ocrData
                .stream()
                .filter(ocrData -> ocrData.name.equals(surnameOcrFieldName) && StringUtils.hasText(ocrData.value))
                .map(ocrData -> ocrData.value)
                .collect(toList());

            if (!surnameList.isEmpty()) {

                if (surnameList.size() > 1) {
                    LOGGER.error(
                        "Surname found {} times in OCR data. "
                            + "Surname Ocr Field Name: {}, Envelope id: {}, Case Ref: {}, Jurisdiction: {}",
                        surnameList.size(),
                        surnameOcrFieldName,
                        envelope.id,
                        envelope.caseRef,
                        envelope.jurisdiction
                    );
                }
                return surnameList.get(0);

            }
        }

        LOGGER.info(
            "Surname not found in OCR data. Envelope id: {}, Case Ref: {}, Jurisdiction: {}",
            envelope.id,
            envelope.caseRef,
            envelope.jurisdiction
        );
        return null;

    }
}
