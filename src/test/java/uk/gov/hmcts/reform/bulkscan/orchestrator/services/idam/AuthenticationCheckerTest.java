package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import com.google.common.collect.ImmutableMap;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationCheckerTest {

    private static final String SUCCESSFUL_JURISDICTION = "jurisdiction";
    private static final String SUCCESSFUL_JURISDICTION_USERNAME = "username1";
    private static final String SUCCESSFUL_JURISDICTION_PASSWORD = "password1";

    private static final String LOCKED_ACCOUNT_JURISDICTION = "locked";
    private static final String LOCKED_ACCOUNT_JURISDICTION_USERNAME = "username2";
    private static final String LOCKED_ACCOUNT_JURISDICTION_PASSWORD = "password2";

    private static final Map<String, Map<String, String>> USERS = ImmutableMap.of(
        SUCCESSFUL_JURISDICTION, ImmutableMap.of(
            "username", SUCCESSFUL_JURISDICTION_USERNAME,
            "password", SUCCESSFUL_JURISDICTION_PASSWORD
        ),
        LOCKED_ACCOUNT_JURISDICTION, ImmutableMap.of(
            "username", LOCKED_ACCOUNT_JURISDICTION_USERNAME,
            "password", LOCKED_ACCOUNT_JURISDICTION_PASSWORD
        )
    );

    @Mock
    private IdamClient idamClient;

    private AuthenticationChecker authenticationChecker;

    @Before
    public void setUp() {
        JurisdictionToUserMapping mapping = new JurisdictionToUserMapping();
        mapping.setUsers(USERS);

        authenticationChecker = new AuthenticationChecker(mapping, idamClient);
    }

    @Test
    public void checkSignInForJurisdiction_should_return_success_for_successfully_authenticated_jurisdiction() {
        willReturn("token")
            .given(idamClient)
            .authenticateUser(
                SUCCESSFUL_JURISDICTION_USERNAME,
                SUCCESSFUL_JURISDICTION_PASSWORD
            );


        JurisdictionConfigurationStatus status =
            authenticationChecker.checkSignInForJurisdiction(SUCCESSFUL_JURISDICTION);

        assertThat(status.jurisdiction).isEqualTo(SUCCESSFUL_JURISDICTION);
        assertThat(status.isCorrect).isTrue();
        assertThat(status.errorDescription).isNull();
    }

    @Test
    public void checkSignInForJurisdiction_should_return_failure_for_unsuccessfully_authenticated_jurisdiction() {
        FeignException exception = createFeignException(HttpStatus.LOCKED.value());

        willThrow(exception)
            .given(idamClient)
            .authenticateUser(LOCKED_ACCOUNT_JURISDICTION_USERNAME, LOCKED_ACCOUNT_JURISDICTION_PASSWORD);

        JurisdictionConfigurationStatus status =
            authenticationChecker.checkSignInForJurisdiction(LOCKED_ACCOUNT_JURISDICTION);

        assertThat(status.jurisdiction).isEqualTo(LOCKED_ACCOUNT_JURISDICTION);
        assertThat(status.isCorrect).isFalse();
        assertThat(status.errorDescription).isEqualTo(exception.getMessage());
    }

    @Test
    public void checkSignInForJurisdiction_should_return_failure_for_jurisdiction_missing_in_config() {
        String unknownJurisdiction = "unknown";

        assertThat(
            authenticationChecker.checkSignInForJurisdiction(unknownJurisdiction)
        )
            .isEqualToComparingFieldByField(
                new JurisdictionConfigurationStatus(
                    unknownJurisdiction,
                    false,
                    String.format("No user configured for jurisdiction: %s", unknownJurisdiction)
                )
            );

        verify(idamClient, never()).authenticateUser(anyString(), anyString());
    }

    @Test
    public void checkSignInForJurisdiction_should_return_failure_when_idam_call_fails() {
        String errorMessage = "test exception";
        RuntimeException exception = new RuntimeException(errorMessage);

        willThrow(exception).given(idamClient).authenticateUser(any(), any());

        assertThat(authenticationChecker.checkSignInForJurisdiction(LOCKED_ACCOUNT_JURISDICTION))
            .isEqualToComparingFieldByField(new JurisdictionConfigurationStatus(
                LOCKED_ACCOUNT_JURISDICTION,
                false,
                errorMessage
            ));
    }

    @Test
    public void checkSignInForAllJurisdictions_should_return_statuses_of_all_jurisdictions() {
        willReturn("token")
            .given(idamClient)
            .authenticateUser(SUCCESSFUL_JURISDICTION_USERNAME, SUCCESSFUL_JURISDICTION_PASSWORD);

        willThrow(createFeignException(HttpStatus.LOCKED.value()))
            .given(idamClient)
            .authenticateUser(LOCKED_ACCOUNT_JURISDICTION_USERNAME, LOCKED_ACCOUNT_JURISDICTION_PASSWORD);

        assertThat(authenticationChecker.checkSignInForAllJurisdictions())
            .extracting(status -> tuple(status.jurisdiction, status.isCorrect))
            .containsExactlyInAnyOrder(
                tuple(SUCCESSFUL_JURISDICTION, true),
                tuple(LOCKED_ACCOUNT_JURISDICTION, false)
            )
            .as("Result should contain a correct entry for each configured jurisdiction");
    }

    private FeignException createFeignException(int httpStatus) {
        return FeignException
            .errorStatus("method1", Response
                .builder()
                .request(mock(Request.class))
                .body(new byte[0])
                .headers(Collections.emptyMap())
                .status(httpStatus)
                .build()
            );
    }
}
