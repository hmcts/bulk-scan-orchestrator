package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventSummaryCreatorTest {

    @Test
    void should_return_full_list_if_size_not_extends_the_limit() {

        String eventSummary = EventSummaryCreator
            .createEventSummary(21312321L, 789757575L, List.of("31321", "755756", "5665"));

        assertThat(eventSummary)
            .isEqualTo("Attaching exception record(789757575) document numbers:[31321, 755756, 5665] to case:21312321");
        assertThat(eventSummary.length()).isLessThan(1024);
    }

    @Test
    void should_trim_the_list_if_size_extends_the_limit() {

        List<String> docList = Stream
            .iterate(1000000000000000L, t -> t + 2)
            .map(t -> t.toString())
            .limit(56)
            .collect(Collectors.toList());

        String eventSummary = EventSummaryCreator
            .createEventSummary(2131232198L, 7697173575L, docList);

        assertThat(eventSummary)
            .startsWith("Attaching exception record(7697173575) document numbers:")
            .contains(docList.subList(0, 52))
            .endsWith(" to case:2131232198");
        assertThat(eventSummary.length()).isLessThan(1024);
    }
}