package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.Map;

@Service
public class UserService {

    private final IdamClient idamClient;
    private final Map<String, Credential> users;

    @Autowired
    public UserService(IdamClient idamClient, @Value("idam.users") Map<String, Credential> users) {
        this.idamClient = idamClient;
        this.users = users;
    }

    public String getBearerTokenForJurisdiction(String jurisdiction) {
        Credential user = users.get(jurisdiction);
        return idamClient.authenticateUser(user.getUsername(), user.getPassword());
    }
}
