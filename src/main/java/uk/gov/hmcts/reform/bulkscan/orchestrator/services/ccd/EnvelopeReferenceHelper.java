package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Service
public class EnvelopeReferenceHelper {
    private static final Logger log = LoggerFactory.getLogger(EnvelopeReferenceHelper.class);

    private final ObjectMapper objectMapper;
    private final ServiceConfigProvider serviceConfigProvider;

    public EnvelopeReferenceHelper(
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
            log.info("envelope references NULL");
            return emptyList();
        } else {
            return rawEnvelopeReferences
                .stream()
                .map(ref -> objectMapper.convertValue(ref.get("value"), EnvelopeReference.class))
                .peek(ref -> log.info("existing refs " + ref.id, ref.action))
                .map(CcdCollectionElement::new)
                .collect(toList());
        }
    }
}
