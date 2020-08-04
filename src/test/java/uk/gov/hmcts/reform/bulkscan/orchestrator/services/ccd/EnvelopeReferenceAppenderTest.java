package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class EnvelopeReferenceAppenderTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    private EnvelopeReferenceAppender appender;

    @BeforeEach
    void setUp() {
        appender = new EnvelopeReferenceAppender(objectMapper, serviceConfigProvider);
    }

    @Test
    void should_return_new_list_when_null_is_provided() {
        // given
        setupCaseDefinitionHasEnvelopeIds(true);

        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.UPDATE;

        // when
        var result = appender.tryAppendEnvelopeReference("service1", null, envelopeId, caseAction);

        // then
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
        // given
        setupCaseDefinitionHasEnvelopeIds(true);

        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.CREATE;

        // when
        var result = appender.tryAppendEnvelopeReference("serivce1", emptyList(), envelopeId, caseAction);

        // then
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
        // given
        setupCaseDefinitionHasEnvelopeIds(true);

        var existingEnvelopeReferences = Arrays.<Map<String, Object>>asList(
            singletonMap("value", ImmutableMap.of("id", "id1", "action", "create")),
            singletonMap("value", ImmutableMap.of("id", "id2", "action", "update"))
        );

        // when
        var result = appender.tryAppendEnvelopeReference(
            "service1",
            existingEnvelopeReferences,
            "id3",
            CaseAction.UPDATE
        );

        // then
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
        setupCaseDefinitionHasEnvelopeIds(false);

        var result = appender.tryAppendEnvelopeReference(
            "service1",
            emptyList(),
            "envelopeId1",
            CaseAction.CREATE);

        assertThat(result).isEmpty();
    }

    private void setupCaseDefinitionHasEnvelopeIds(boolean caseDefinitionHasEnvelopeIds) {
        ServiceConfigItem serviceConfigItem = new ServiceConfigItem();
        serviceConfigItem.setCaseDefinitionHasEnvelopeIds(caseDefinitionHasEnvelopeIds);
        given(serviceConfigProvider.getConfig(any())).willReturn(serviceConfigItem);
    }
}
