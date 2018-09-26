package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

@Service
class CcdAuthService {
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamClient idamClient;
    private final JurisdictionToUserMapping users;

    public CcdAuthService(AuthTokenGenerator s2sTokenGenerator,
                          IdamClient idamClient,
                          JurisdictionToUserMapping users) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.idamClient = idamClient;
        this.users = users;
    }

    CcdAuthInfo authenticateForJurisdiction(String jurisdiction) {
        String sscsToken = s2sTokenGenerator.generate();
        Credential user = users.getUser(jurisdiction);
        String userToken = idamClient.authenticateUser(user.getUsername(), user.getPassword());
        UserDetails userDetails = idamClient.getUserDetails(userToken);

        return new CcdAuthInfo(sscsToken, user, userToken, userDetails);
    }
}
