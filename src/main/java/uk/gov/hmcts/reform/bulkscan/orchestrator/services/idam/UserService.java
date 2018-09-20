package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.idam.client.IdamClient;

@Service
public class UserService {

    private final IdamClient idamClient;
    private final UserMapping users;

    @Autowired
    public UserService(IdamClient idamClient, UserMapping users) {
        this.idamClient = idamClient;
        this.users = users;
    }

    public String getBearerTokenForJurisdiction(String jurisdiction) {
        Credential user = users.getUsers().get(jurisdiction);
        if (user == null) {
            throw new NoUserConfiguredException(jurisdiction);
        }

        return idamClient.authenticateUser(user.getUsername(), user.getPassword());
    }
}
