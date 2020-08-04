package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfiguration;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
class EnvelopeReferenceAppenderTest {

    private static final String ENABLED_SERVICE_NAME = "enabledService1";
    private static final String DISABLED_SERVICE_NAME = "disabledService1";

    private ObjectMapper objectMapper = new ObjectMapper();
    private ServiceConfiguration serviceConfiguration = mock(ServiceConfiguration.class);

    private EnvelopeReferenceAppender appender;

    @BeforeEach
    void setUp() {
        given(serviceConfiguration.getServices()).willReturn(
            asList(
                serviceConfigItem(ENABLED_SERVICE_NAME, true),
                serviceConfigItem(DISABLED_SERVICE_NAME, false)
            )
        );

        appender = new EnvelopeReferenceAppender(objectMapper, serviceConfiguration);
    }

    @Test
    void should_return_new_list_when_null_is_provided() {
        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.UPDATE;

        var result = appender.tryAppendEnvelopeReference(ENABLED_SERVICE_NAME, null, envelopeId, caseAction);

        var expectedResult = Optional.of(
            asList(
                new CcdCollectionElement(
                    new EnvelopeReference(envelopeId, "update")
                )
            )
        );

        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
    }

    @Test
    void should_append_envelope_reference_to_empty_list() {
        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.CREATE;

        var result = appender.tryAppendEnvelopeReference(ENABLED_SERVICE_NAME, emptyList(), envelopeId, caseAction);

        var expectedResult = Optional.of(
            asList(
                new CcdCollectionElement(
                    new EnvelopeReference(envelopeId, "create")
                )
            )
        );

        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
    }

    @Test
    void should_append_envelope_reference_to_non_empty_list() {
        var existingEnvelopeReferences = Arrays.<Map<String, Object>>asList(
            singletonMap("value", ImmutableMap.of("id", "id1", "action", "create")),
            singletonMap("value", ImmutableMap.of("id", "id2", "action", "update"))
        );

        var result = appender.tryAppendEnvelopeReference(
            ENABLED_SERVICE_NAME,
            existingEnvelopeReferences,
            "id3",
            CaseAction.UPDATE
        );

        var expectedResult = Optional.of(
            asList(
                new CcdCollectionElement(new EnvelopeReference("id1", "create")),
                new CcdCollectionElement(new EnvelopeReference("id2", "update")),
                new CcdCollectionElement(new EnvelopeReference("id3", "update"))
            )
        );

        assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
    }

    @Test
    void should_not_append_envelope_reference_when_service_is_disabled() {
        var result = appender.tryAppendEnvelopeReference(
            DISABLED_SERVICE_NAME,
            emptyList(),
            "envelopeId1",
            CaseAction.CREATE);

        assertThat(result).isEmpty();
    }

    private ServiceConfigItem serviceConfigItem(String serviceName, boolean caseDefinitionHasEnvelopeIds) {
        ServiceConfigItem serviceConfigItem = new ServiceConfigItem();
        serviceConfigItem.setService(serviceName);
        serviceConfigItem.setCaseDefinitionHasEnvelopeIds(caseDefinitionHasEnvelopeIds);
        return serviceConfigItem;
    }
}
