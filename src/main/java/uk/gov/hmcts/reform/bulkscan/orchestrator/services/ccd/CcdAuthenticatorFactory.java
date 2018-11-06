package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

@Service
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class CcdAuthenticatorFactory {

    private static final Logger log = LoggerFactory.getLogger(CcdAuthenticatorFactory.class);

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

    public CcdAuthenticator createForJurisdiction(String jurisdiction) {
        Credential user = users.getUser(jurisdiction);
        log.debug("Authenticating user: {}", user.getUsername());
        String userToken = idamClient.authenticateUser(user.getUsername(), user.getPassword());
        UserDetails userDetails = idamClient.getUserDetails(userToken);

        //TODO: RPE-738 the userToken needs a to be cached and timed-out.
        // this can be decorated here like the s2sTokenGenerator
        return CcdAuthenticator.from(s2sTokenGenerator::generate, userDetails, () -> userToken);
    }
}
