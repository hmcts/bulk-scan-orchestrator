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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@Service
public class EnvelopeReferenceCollectionHelper {

    private final ObjectMapper objectMapper;
    private final ServiceConfigProvider serviceConfigProvider;

    public EnvelopeReferenceCollectionHelper(
        ObjectMapper objectMapper,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.objectMapper = objectMapper;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    public boolean serviceSupportsEnvelopeReferences(String service) {
        return serviceConfigProvider.getConfig(service).getCaseDefinitionHasEnvelopeIds();
    }

    /**
     * Appends envelope reference to the provided envelope references collection.
     *
     * <p>This collection represents bulkScanEnvelopes field in service case</p>
     *
     * @param existingEnvelopeReferences Current value of the field that holds envelope references in case,
     *                                   in raw format (after deserialising case data as Object)
     * @param envelopeId                 Id of the envelope to be appended
     * @param action                     Action that the envelope caused on the case - create/update
     * @return Updated collection, serialisable to CCD-compatible format
     */
    public List<CcdCollectionElement<EnvelopeReference>> appendEnvelopeReference(
        List<Map<String, Object>> existingEnvelopeReferences,
        String envelopeId,
        CaseAction action
    ) {
        if (CollectionUtils.isEmpty(existingEnvelopeReferences)) {
            return singleEnvelopeReferenceList(envelopeId, action);
        } else {
            var updatedReferences = existingEnvelopeReferences
                .stream()
                .map(ref -> objectMapper.convertValue(ref.get("value"), EnvelopeReference.class))
                .map(CcdCollectionElement::new)
                .collect(toList());

            updatedReferences.addAll(singleEnvelopeReferenceList(envelopeId, action));

            return updatedReferences;
        }
    }

    public List<CcdCollectionElement<EnvelopeReference>> singleEnvelopeReferenceList(
        String envelopeId,
        CaseAction action
    ) {
        return asList(
            new CcdCollectionElement<>(new EnvelopeReference(envelopeId, action.value))
        );
    }
}
