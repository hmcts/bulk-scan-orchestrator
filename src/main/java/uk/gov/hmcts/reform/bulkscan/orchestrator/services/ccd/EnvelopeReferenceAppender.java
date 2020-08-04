package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfiguration;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * Modifies case's bulkScanEnvelopes field by (conditionally) appending envelope information to existing items.
 */
@Service
public class EnvelopeReferenceAppender {

    private final ObjectMapper objectMapper;
    private final Set<String> enabledServices;

    public EnvelopeReferenceAppender(
        ObjectMapper objectMapper,
        ServiceConfiguration serviceConfiguration
    ) {
        this.objectMapper = objectMapper;

        enabledServices = serviceConfiguration
            .getServices()
            .stream()
            .filter(ServiceConfigItem::getCaseDefinitionHasEnvelopeIds)
            .map(ServiceConfigItem::getService)
            .collect(Collectors.toSet());
    }

    /**
     * Appends envelope reference to the existing envelope references collection.
     *
     * <p>This collection represents bulkScanEnvelopes field in service case and the update will only take place
     * if the service supports this field (i.e. has it in all its CCD case definitions).</p>
     *
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
        if (enabledServices.contains(service)) {
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
        if (existingEnvelopeReferences == null || existingEnvelopeReferences.isEmpty()) {
            return asList(
                new CcdCollectionElement<>(new EnvelopeReference(envelopeId, action.value))
            );
        } else {
            List<EnvelopeReference> updatedReferences = existingEnvelopeReferences
                .stream()
                .map(ref -> objectMapper.convertValue(ref.get("value"), EnvelopeReference.class))
                .collect(toList());

            updatedReferences.add(new EnvelopeReference(envelopeId, action.value));

            return updatedReferences
                .stream()
                .map(ref -> new CcdCollectionElement<>(ref))
                .collect(toList());
        }
    }
}
