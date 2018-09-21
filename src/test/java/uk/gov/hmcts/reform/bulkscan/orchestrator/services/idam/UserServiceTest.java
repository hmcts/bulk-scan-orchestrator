package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.HashMap;

import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {

    @Mock
    private IdamClient idamClient;

    @Mock
    private JurisdictionToUserMapping jurisdictionToUserMapping;

    private UserService userService;

    @Before
    public void before() {
        userService = new UserService(idamClient, jurisdictionToUserMapping);
    }

    @Test(expected = NoUserConfiguredException.class)
    public void getBearerTokenForJurisdiction_will_throw_if_unknown_jurisdiction() {
        given(jurisdictionToUserMapping.getUsers()).willReturn(new HashMap<>());

        userService.getBearerTokenForJurisdiction("some-jurisdiction");
    }
}
