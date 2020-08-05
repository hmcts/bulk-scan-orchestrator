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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class EnvelopeReferenceCollectionHelperTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ServiceConfigProvider serviceConfigProvider;

    private EnvelopeReferenceCollectionHelper envelopeReferenceCollectionHelper;

    @BeforeEach
    void setUp() {
        envelopeReferenceCollectionHelper = new EnvelopeReferenceCollectionHelper(objectMapper, serviceConfigProvider);
    }

    @Test
    void appendEnvelopeReference_should_return_new_list_when_null_is_provided() {
        // given
        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.UPDATE;

        // when
        var result = envelopeReferenceCollectionHelper.appendEnvelopeReference(
            null,
            envelopeId,
            caseAction
        );

        // then
        var expectedResult = asList(
            new CcdCollectionElement(new EnvelopeReference(envelopeId, "update"))
        );

        assertThat(result)
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(expectedResult);
    }

    @Test
    void appendEnvelopeReference_should_append_envelope_reference_to_empty_list() {
        // given
        String envelopeId = "envelopeId1";
        CaseAction caseAction = CaseAction.CREATE;

        // when
        var result = envelopeReferenceCollectionHelper.appendEnvelopeReference(emptyList(), envelopeId, caseAction);

        // then
        var expectedResult = asList(new CcdCollectionElement(new EnvelopeReference(envelopeId, "create")));

        assertThat(result)
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(expectedResult);
    }

    @Test
    void appendEnvelopeReference_should_append_envelope_reference_to_non_empty_list() {
        // given
        var existingEnvelopeReferences = Arrays.<Map<String, Object>>asList(
            singletonMap("value", ImmutableMap.of("id", "id1", "action", "create")),
            singletonMap("value", ImmutableMap.of("id", "id2", "action", "update"))
        );

        // when
        var result = envelopeReferenceCollectionHelper.appendEnvelopeReference(
            existingEnvelopeReferences,
            "id3",
            CaseAction.UPDATE
        );

        // then
        var expectedResult = asList(
            new CcdCollectionElement(new EnvelopeReference("id1", "create")),
            new CcdCollectionElement(new EnvelopeReference("id2", "update")),
            new CcdCollectionElement(new EnvelopeReference("id3", "update"))
        );

        assertThat(result)
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(expectedResult);
    }


    @Test
    void serviceSupportsEnvelopeReferences_should_return_correct_value_based_on_configuration() {
        String enabledServiceName = "enabledService1";
        String disabledServiceName = "disabledService1";

        setupCaseDefinitionHasEnvelopeIds(enabledServiceName, true);
        setupCaseDefinitionHasEnvelopeIds(disabledServiceName, false);

        assertThat(
            envelopeReferenceCollectionHelper.serviceSupportsEnvelopeReferences(enabledServiceName)
        )
            .isTrue();

        assertThat(
            envelopeReferenceCollectionHelper.serviceSupportsEnvelopeReferences(disabledServiceName)
        )
            .isFalse();
    }

    @Test
    void singleEnvelopeReferenceList_should_return_single_element_list_with_provided_data() {
        String envelopeId = "envelope1";

        var result = envelopeReferenceCollectionHelper.singleEnvelopeReferenceList(
            envelopeId,
            CaseAction.CREATE
        );

        assertThat(result)
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(
                asList(new CcdCollectionElement(new EnvelopeReference(envelopeId, "create")))
            );
    }

    private void setupCaseDefinitionHasEnvelopeIds(String serviceName, boolean caseDefinitionHasEnvelopeIds) {
        ServiceConfigItem serviceConfigItem = new ServiceConfigItem();
        serviceConfigItem.setCaseDefinitionHasEnvelopeIds(caseDefinitionHasEnvelopeIds);
        given(serviceConfigProvider.getConfig(serviceName)).willReturn(serviceConfigItem);
    }
}
