package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import com.google.common.collect.ImmutableMap;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class IdamConfigStatusEndpointTest {

    private static final String VALID_JURISDICTION = "jurisdiction";

    private static final String INVALID_JURISDICTION = "something else";

    private static final JurisdictionConfigurationStatus VALID_RESPONSE = new JurisdictionConfigurationStatus(
        VALID_JURISDICTION, true
    );

    private static final JurisdictionConfigurationStatus INVALID_RESPONSE = new JurisdictionConfigurationStatus(
        INVALID_JURISDICTION, false, "oh no"
    );

    @Mock
    private IdamClient idamClient;

    private IdamConfigStatusEndpoint endpoint;

    @BeforeEach
    public void setUp() {
        Map<String, Map<String, String>> users = ImmutableMap.of(
            VALID_JURISDICTION, ImmutableMap.of(
                "username", "username",
                "password", "password"
            ),
            INVALID_JURISDICTION, ImmutableMap.of(
                "username", "user",
                "password", "pass"
            )
        );
        JurisdictionToUserMapping mapping = new JurisdictionToUserMapping();
        mapping.setUsers(users);

        endpoint = new IdamConfigStatusEndpoint(mapping, idamClient);
    }

    @DisplayName("Should respond with status message stating given jurisdiction is correctly configured")
    @Test
    public void should_respond_accordingly_for_correct_jurisdiction_config() {
        willReturn("token").given(idamClient).authenticateUser("username", "password");

        assertThat(endpoint.jurisdiction(VALID_JURISDICTION)).isEqualToComparingFieldByField(VALID_RESPONSE);
    }

    @DisplayName("Should respond with expected status given jurisdiction is configured incorrectly")
    @Test
    public void should_respond_accordingly_for_incorrect_jurisdiction_config() {
        willThrow(new RuntimeException("oh no")).given(idamClient).authenticateUser("user", "pass");

        assertThat(endpoint.jurisdiction(INVALID_JURISDICTION)).isEqualToComparingFieldByField(INVALID_RESPONSE);
    }

    @DisplayName("Should respond with expected status given jurisdiction does not exist in the configuration setup")
    @Test
    public void should_respond_accordingly_for_jurisdiction_which_is_not_present_in_config() {
        assertThat(endpoint.jurisdiction("not give access")).isEqualToComparingFieldByField(
            new JurisdictionConfigurationStatus(
                "not give access",
                false,
                "No user configured for jurisdiction: not give access"
            )
        );
        verify(idamClient, never()).authenticateUser(anyString(), anyString());
    }

    @DisplayName("Should respond with all configured jurisdictions and not crash with exceptions")
    @Test
    public void should_respond_without_failure_when_requesting_info_on_all_jurisdictions() {
        willReturn("token").given(idamClient).authenticateUser("username", "password");
        willThrow(new RuntimeException("oh no")).given(idamClient).authenticateUser("user", "pass");

        assertThat(endpoint.jurisdictions())
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactly(VALID_RESPONSE, INVALID_RESPONSE);
    }

    @DisplayName("Should respond with status message for given jurisdiction in case FeignException is received")
    @Test
    public void should_respond_as_incorrect_setup_when_feign_client_exception_is_received() {
        FeignException exception = FeignException
            .errorStatus("method key", Response
                .builder()
                .request(mock(Request.class))
                .body(new byte[0])
                .headers(Collections.emptyMap())
                .status(HttpStatus.FORBIDDEN.value())
                .build()
            );

        willThrow(exception).given(idamClient).authenticateUser("user", "pass");

        // not asserting an object as depends on feign version what message it has inside.
        // plus it is not of importance for unit test.
        // we have other cases covering the fact description is provided as expected.
        assertThat(endpoint.jurisdiction(INVALID_JURISDICTION))
            .extracting("jurisdiction", "isCorrect")
            .containsExactly(tuple(INVALID_JURISDICTION, false).toArray());
    }
}
