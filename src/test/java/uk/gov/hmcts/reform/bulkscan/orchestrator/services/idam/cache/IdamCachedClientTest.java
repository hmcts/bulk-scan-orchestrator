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
    private IdamClient idamApi;
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
            idamApi,
            users,
            new AccessTokenCacheExpiry(refreshTokenBeforeExpiry)
        );
    }

    @Test
    public void should_get_credentials_when_no_error() {
        String jurisdiction = "divorce";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);
        given(idamApi.getUserDetails(JWT)).willReturn(USER_DETAILS);

        CachedIdamCredential cachedIdamCredential =
            idamCachedClient.getIdamCredentials(jurisdiction);

        assertThat(cachedIdamCredential.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential.userDetails).usingRecursiveComparison()
            .isEqualTo(USER_DETAILS);
        verify(users).getUser(jurisdiction);
        verify(idamApi).authenticateUser(USERNAME, PASSWORD);
        verify(idamApi).getUserDetails(JWT);
    }

    @Test
    public void should_cache_by_jurisdiction() {
        String jurisdiction1 = "divorce";
        String jurisdiction2 = "cmc";

        given(users.getUser(jurisdiction1)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);
        given(idamApi.getUserDetails(JWT)).willReturn(USER_DETAILS);

        UserDetails expectedUserDetails2 =  new UserDetails("12","q@a.com","","",null);
        given(users.getUser(jurisdiction2)).willReturn(new Credential(USERNAME + 2, PASSWORD + 2));
        given(idamApi.authenticateUser(USERNAME + 2, PASSWORD + 2)).willReturn(JWT2);
        given(idamApi.getUserDetails(JWT2)).willReturn(expectedUserDetails2);

        CachedIdamCredential cachedIdamCredential1 =
            idamCachedClient.getIdamCredentials(jurisdiction1);
        CachedIdamCredential cachedIdamCredential2 =
            idamCachedClient.getIdamCredentials(jurisdiction2);

        assertThat(cachedIdamCredential1.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential1.userDetails).isEqualTo(USER_DETAILS);
        assertThat(cachedIdamCredential2.accessToken).isEqualTo(JWT2);
        assertThat(cachedIdamCredential2.userDetails).isEqualTo(expectedUserDetails2);

        verify(users, times(2)).getUser(any());
        verify(idamApi, times(2)).authenticateUser(any(), any());
        verify(idamApi, times(2)).getUserDetails(any());

    }

    @Test
    void should_retrieve_token_from_cache_when_value_in_cache() {
        String jurisdiction = "bulkscan";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT);
        given(idamApi.getUserDetails(JWT)).willReturn(USER_DETAILS);

        CachedIdamCredential cachedIdamCredential1 =
            idamCachedClient.getIdamCredentials(jurisdiction);

        CachedIdamCredential cachedIdamCredential2 =
            idamCachedClient.getIdamCredentials(jurisdiction);

        assertThat(cachedIdamCredential1.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential1).usingRecursiveComparison().isEqualTo(
            cachedIdamCredential2);
        verify(users).getUser(any());
        verify(idamApi).authenticateUser(any(), any());
        verify(idamApi).getUserDetails(any());
    }

    @Test
    void should_throw_exception_when_expiry_missing() {
        String jurisdiction = "probate";
        String jwt = BEARER_AUTH_TYPE + "eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxnbzEifQ==."
            + "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImp0aSI6ImQ5YmVkMTcxLWZlNzUtNDE"
            + "4YS04Mjc2LTBjNTkzZWMzNzFhOCIsImlhdCI6MTU4NzY1Mzc4NiwiZXhwIjoxNTg3NjU3Mzg2fQ=="
            + ".ttsad3tttvfve";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(jwt);

        assertThatThrownBy(() -> idamCachedClient.getIdamCredentials(jurisdiction))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Invalid idam token, 'expires_in' is missing.");
    }

    @Test
    void should_throw_exception_when_token_decoding_fails() {
        String jurisdiction = "probate";

        String jwt = "eyJ0eXAiOiJKV1QiLCJ6aXAiOiJOT05FIiwiYWxnIjoiYWxn";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(jwt);

        assertThatThrownBy(() -> idamCachedClient.getIdamCredentials(jurisdiction))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Idam token decoding error.");
    }

    @Test
    void should_create_token_when_cache_is_expired() throws InterruptedException {

        IdamCachedClient idamCachedClientQuickExpiry = new IdamCachedClient(
            idamApi,
            users,
            new AccessTokenCacheExpiry(28798)
        );

        String jurisdiction = "probate";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT, JWT2);
        given(idamApi.getUserDetails(JWT)).willReturn(USER_DETAILS);
        UserDetails expectedUserDetails2 =  new UserDetails("12","q@a.com","","",null);
        given(idamApi.getUserDetails(JWT2)).willReturn(expectedUserDetails2);

        CachedIdamCredential cachedIdamCredential1 =
            idamCachedClientQuickExpiry.getIdamCredentials(jurisdiction);

        TimeUnit.SECONDS.sleep(3);

        CachedIdamCredential cachedIdamCredential2 =
            idamCachedClientQuickExpiry.getIdamCredentials(jurisdiction);

        assertThat(cachedIdamCredential1.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential1.userDetails).isEqualTo(USER_DETAILS);
        assertThat(cachedIdamCredential2.accessToken).isEqualTo(JWT2);
        assertThat(cachedIdamCredential2.userDetails).isEqualTo(expectedUserDetails2);

        assertThat(cachedIdamCredential1).isNotEqualTo(cachedIdamCredential2);
        verify(users, times(2)).getUser(any());
        verify(idamApi, times(2)).authenticateUser(any(), any());
        verify(idamApi, times(2)).getUserDetails(any());
    }

    @Test
    void should_create_new_token_when_token_removed_from_cache() {

        String jurisdiction = "probate";

        given(users.getUser(jurisdiction)).willReturn(new Credential(USERNAME, PASSWORD));
        given(idamApi.authenticateUser(USERNAME, PASSWORD)).willReturn(JWT, JWT2);
        given(idamApi.getUserDetails(JWT)).willReturn(USER_DETAILS);
        UserDetails expectedUserDetails2 =  new UserDetails("1122","12q@a.com","joe","doe",null);

        given(idamApi.getUserDetails(JWT2)).willReturn(expectedUserDetails2);

        CachedIdamCredential cachedIdamCredential1 = idamCachedClient.getIdamCredentials(jurisdiction);

        idamCachedClient.removeAccessTokenFromCache(jurisdiction);

        CachedIdamCredential cachedIdamCredential2 = idamCachedClient.getIdamCredentials(jurisdiction);

        assertThat(cachedIdamCredential1.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential1.userDetails).isEqualTo(USER_DETAILS);
        assertThat(cachedIdamCredential1.accessToken).isEqualTo(JWT);
        assertThat(cachedIdamCredential2.userDetails).isEqualTo(expectedUserDetails2);

        verify(users, times(2)).getUser(any());
        verify(idamApi, times(2)).authenticateUser(any(), any());
        verify(idamApi, times(2)).getUserDetails(any());
    }

}