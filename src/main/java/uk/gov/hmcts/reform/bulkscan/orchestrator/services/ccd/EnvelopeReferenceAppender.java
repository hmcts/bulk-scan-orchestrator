package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * Modifies case's bulkScanEnvelopes field by (conditionally) appending envelope information to existing items.
 */
@Service
public class EnvelopeReferenceAppender {

    private final ObjectMapper objectMapper;
    private final ServiceConfigProvider serviceConfigProvider;

    public EnvelopeReferenceAppender(
        ObjectMapper objectMapper,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.objectMapper = objectMapper;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    /**
     * Appends envelope reference to the existing envelope references collection.
     *
     * <p>This collection represents bulkScanEnvelopes field in service case and the update will only take place
     * if the service supports this field (i.e. has it in all its CCD case definitions).</p>
     *
     * @param service Name of the service that owns the case
     * @param existingEnvelopeReferences Current value of the field that holds envelope references in case,
     *                                   in raw format (after deserialising case data as Object)
     * @param envelopeId Id of the envelope to be appended
     * @param action Action that the envelope caused on the case - create/update
     * @return Updated collection, if envelope info was appended. Otherwise, if the CCD case definition
     *         for the service doesn't support envelope reference collection (bulkScanEnvelopes field),
     *         empty Optional is returned.
     */
    public Optional<List<CcdCollectionElement<EnvelopeReference>>> tryAppendEnvelopeReference(
        String service,
        List<Map<String, Object>> existingEnvelopeReferences,
        String envelopeId,
        CaseAction action
    ) {
        if (serviceConfigProvider.getConfig(service).getCaseDefinitionHasEnvelopeIds()) {
            return Optional.of(
                appendReference(existingEnvelopeReferences, envelopeId, action)
            );
        } else {
            return Optional.empty();
        }
    }

    private List<CcdCollectionElement<EnvelopeReference>> appendReference(
        List<Map<String, Object>> existingEnvelopeReferences,
        String envelopeId,
        CaseAction action
    ) {
        if (CollectionUtils.isEmpty(existingEnvelopeReferences)) {
            return asList(
                new CcdCollectionElement<>(new EnvelopeReference(envelopeId, action.value))
            );
        } else {
            var updatedReferences = existingEnvelopeReferences
                .stream()
                .map(ref -> objectMapper.convertValue(ref.get("value"), EnvelopeReference.class))
                .map(CcdCollectionElement::new)
                .collect(toList());

            updatedReferences.add(new CcdCollectionElement<>(new EnvelopeReference(envelopeId, action.value)));

            return updatedReferences;
        }
    }
}
