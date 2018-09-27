package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

@Service
@Component
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
class CcdAuthenticatorFactory {
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamClient idamClient;
    private final JurisdictionToUserMapping users;

    public CcdAuthenticatorFactory(AuthTokenGenerator s2sTokenGenerator,
                                   IdamClient idamClient,
                                   JurisdictionToUserMapping users) {
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.idamClient = idamClient;
        this.users = users;
    }

    Authenticator createForJurisdiction(String jurisdiction) {
        Credential user = users.getUser(jurisdiction);
        String userToken = idamClient.authenticateUser(user.getUsername(), user.getPassword());
        UserDetails userDetails = idamClient.getUserDetails(userToken);

        //TODO: RPE-738 the userToken needs a to be cached and timed-out.
        // this can be decorated here like the s2sTokenGenerator
        return Authenticator.from(s2sTokenGenerator::generate, userDetails, () -> userToken);
    }
}
