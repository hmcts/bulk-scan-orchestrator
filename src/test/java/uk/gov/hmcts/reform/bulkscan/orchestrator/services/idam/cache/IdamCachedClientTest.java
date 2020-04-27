package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class IdamCachedClientTest {

    @Mock
    private IdamClient idamClient;
    @Mock
    private JurisdictionToUserMapping users;

    private IdamCachedClient idamCachedClient;

    private long refreshTokenBeforeExpiry = 2879;

    public static final String BEARER_AUTH_TYPE = "Bearer ";

    private static final String JWT =
        "Bearer eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxnbzEifQ==."
            + "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImp0aSI6ImQ5YmVkMTcxLWZlNzUtNDE4YS0"
            + "4Mjc2LTBjNTkzZWMzNzFhOCIsImlhdCI6MTU4NzY1Mzc4NiwiZXhwIjoxNTg3NjU3Mzg2LCAiZXhwaXJlc19pbiI6IDI4ODAwfQ=="
            + ".dadsad";

    private static final String JWT2 =
        "Bearer eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxnbzEifQ=="
            + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImp0aSI6ImQ5YmVkMTcxLWZlNzUtNDE"
            + "4YS04Mjc2LTBjNTkzZWMzNzFhOCIsImlhdCI6MTU4NzY1Mzc4NiwiZXhwIjoxNTg"
            + "3NjU3Mzg2LCAiZXhwaXJlc19pbiI6IDEwfQ=="
            + ".xxxxx";

    private static final String USERNAME = "userxxx";

    private static final String PASSWORD = "passs123";

    private static final UserDetails USER_DETAILS = new UserDetails(
        "12",
        "q@a.com",
        "name_x",
        "surname_y",
        Arrays.asList("role1, role2", "role3")
    );

    @BeforeEach
    private void setUp() {
        this.idamCachedClient = new IdamCachedClient(
            idamClient,
            users,
            new AccessTokenCacheExpiry(refreshTokenBeforeExpiry)
        );
    }

    @Test
    public void should_get_token_when_no_error() {
        String jurisdiction = "divorce";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);

        String token =
            idamCachedClient.getAccessToken(jurisdiction);

        assertThat(JWT).isEqualTo(token);
        verify(users).getUser(jurisdiction);
        verify(idamClient).authenticateUser(USERNAME, PASSWORD);

    }

    @Test
    public void should_cache_by_jurisdiction() {
        String jurisdiction1 = "divorce";
        String jurisdiction2 = "cmc";

        given(users.getUser(jurisdiction1)).willReturn(new Credential(USERNAME, PASSWORD));
        given(users.getUser(jurisdiction2)).willReturn(new Credential(USERNAME + 2, PASSWORD + 2));

        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);
        given(idamClient.authenticateUser(USERNAME + 2, PASSWORD + 2)).willReturn(JWT2);

        String token1 =
            idamCachedClient.getAccessToken(jurisdiction1);
        String token2 =
            idamCachedClient.getAccessToken(jurisdiction2);

        assertThat(JWT).isEqualTo(token1);
        assertThat(JWT2).isEqualTo(token2);

        verify(users, times(2)).getUser(any());
        verify(idamClient, times(2)).authenticateUser(any(), any());

    }

    @Test
    void should_retrieve_token_from_cache_when_value_in_cache() {
        String jurisdiction = "bulkscan";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);

        String token1 =
            idamCachedClient.getAccessToken(jurisdiction);

        String token2 =
            idamCachedClient.getAccessToken(jurisdiction);

        assertThat(JWT).isEqualTo(token1);
        assertThat(token1).isEqualTo(token2);
        verify(users).getUser(any());
        verify(idamClient).authenticateUser(any(), any());
    }

    @Test
    void should_throw_exception_when_expiry_missing() {
        String jurisdiction = "probate";
        String jwt = BEARER_AUTH_TYPE + "eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxnbzEifQ==."
            + "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImp0aSI6ImQ5YmVkMTcxLWZlNzUtNDE"
            + "4YS04Mjc2LTBjNTkzZWMzNzFhOCIsImlhdCI6MTU4NzY1Mzc4NiwiZXhwIjoxNTg3NjU3Mzg2fQ=="
            + ".ttsad3tttvfve";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(jwt);

        assertThatThrownBy(() -> idamCachedClient.getAccessToken(jurisdiction))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Invalid idam token, 'expires_in' is missing.");
    }

    @Test
    void should_throw_exception_when_token_decoding_fails() {
        String jurisdiction = "probate";

        String jwt = "eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxn";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(jwt);

        assertThatThrownBy(() -> idamCachedClient.getAccessToken(jurisdiction))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Idam token decoding error.");
    }

    @Test
    void should_create_token_when_cache_is_expired() throws InterruptedException {

        IdamCachedClient idamCachedClientQuickExpiry = new IdamCachedClient(
            idamClient,
            users,
            new AccessTokenCacheExpiry(28798)
        );

        String jurisdiction = "probate";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT, JWT2);
        String token1 =
            idamCachedClientQuickExpiry.getAccessToken(jurisdiction);

        TimeUnit.SECONDS.sleep(3);

        String token2 =
            idamCachedClientQuickExpiry.getAccessToken(jurisdiction);

        assertThat(JWT).isEqualTo(token1);
        assertThat(JWT2).isEqualTo(token2);
        assertThat(token1).isNotEqualTo(token2);
        verify(users, times(2)).getUser(any());
        verify(idamClient, times(2)).authenticateUser(any(), any());
    }

    @Test
    void should_create_new_token_when_token_removed_from_cache() {

        String jurisdiction = "probate";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT, JWT2);
        String token1 = idamCachedClient.getAccessToken(jurisdiction);

        idamCachedClient.removeAccessTokenFromCache(jurisdiction);

        String token2 = idamCachedClient.getAccessToken(jurisdiction);

        assertThat(JWT).isEqualTo(token1);
        assertThat(JWT2).isEqualTo(token2);
        assertThat(token1).isNotEqualTo(token2);
        verify(users, times(2)).getUser(any());
        verify(idamClient, times(2)).authenticateUser(any(), any());
    }


    @Test
    public void should_get_userDetail_when_no_error() {
        given(idamClient.getUserDetails(JWT)).willReturn(USER_DETAILS);

        UserDetails userDetails1 =
            idamCachedClient.getUserDetails(JWT);

        assertThat(userDetails1).usingRecursiveComparison().isEqualTo(USER_DETAILS);
        verify(idamClient).getUserDetails(any());
    }

    @Test
    public void should_retrieve_userDetail_from_cache_when_value_in_cache() {
        given(idamClient.getUserDetails(JWT)).willReturn(USER_DETAILS);

        UserDetails userDetails1 =
            idamCachedClient.getUserDetails(JWT);
        UserDetails userDetails2 =
            idamCachedClient.getUserDetails(JWT);

        assertThat(userDetails1).usingRecursiveComparison().isEqualTo(USER_DETAILS);
        assertThat(userDetails2).usingRecursiveComparison().isEqualTo(userDetails1);
        verify(idamClient).getUserDetails(any());
    }

    @Test
    public void should_invalidate_userDetail_when_cached_token_removed_from_cache() {
        String jurisdiction1 = "divorce";

        given(users.getUser(jurisdiction1)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamClient.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT, JWT2);

        UserDetails expectedUserDetails1 =  new UserDetails("12","q@a.com","","",null);
        UserDetails expectedUserDetails2 = USER_DETAILS;

        given(idamClient.getUserDetails(JWT)).willReturn(expectedUserDetails1, expectedUserDetails2);

        String token1 = idamCachedClient.getAccessToken(jurisdiction1);
        UserDetails userDetailsBefore = idamCachedClient.getUserDetails(token1);

        idamCachedClient.removeAccessTokenFromCache(jurisdiction1);

        assertThat(userDetailsBefore).isEqualTo(expectedUserDetails1);

        UserDetails userDetailsBeforeInvalidating = idamCachedClient.getUserDetails(token1);
        assertThat(userDetailsBeforeInvalidating).usingRecursiveComparison().isEqualTo(expectedUserDetails2);
        verify(idamClient, times(2)).getUserDetails(any());

    }
}