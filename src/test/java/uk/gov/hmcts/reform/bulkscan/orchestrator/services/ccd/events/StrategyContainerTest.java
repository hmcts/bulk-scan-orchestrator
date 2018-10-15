package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;

@RunWith(MockitoJUnitRunner.class)
public class StrategyContainerTest {

    @Mock
    private AttachDocsToSupplementaryEvidence attachDocsToSupplementaryEvidence;

    @InjectMocks
    private StrategyContainer strategyContainer = new StrategyContainer();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void should_get_AttachDocsToSupplementaryEvidence_strategy() throws IOException {
        // when
        Strategy strategy = strategyContainer.getStrategy(
            objectMapper.readValue(
                envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE),
                Envelope.class
            ),
            null
        );

        // then
        assertThat(strategy).isInstanceOf(attachDocsToSupplementaryEvidence.getClass());
    }
}
