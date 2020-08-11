package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
     * Converts the raw CCD collection of envelope references into a strongly-typed version.
     *
     * @param rawEnvelopeReferences Envelope references in case, in raw format
     *                              (after deserialising case data as Object)
     * @return Collection of strongly typed envelope references, serialisable to CCD-compatible format
     */
    public List<CcdCollectionElement<EnvelopeReference>> parseEnvelopeReferences(
        List<Map<String, Object>> rawEnvelopeReferences
    ) {
        if (rawEnvelopeReferences == null) {
            return emptyList();
        } else {
            return rawEnvelopeReferences
                .stream()
                .map(ref -> objectMapper.convertValue(ref.get("value"), EnvelopeReference.class))
                .map(CcdCollectionElement::new)
                .collect(toList());
        }
    }

    public List<CcdCollectionElement<EnvelopeReference>> singleEnvelopeReferenceList(
        String envelopeId,
        CaseAction action
    ) {
        return asList(
            new CcdCollectionElement<>(new EnvelopeReference(envelopeId, action))
        );
    }
}
