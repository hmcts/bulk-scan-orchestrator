package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.OAuth2Configuration;

import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class AuthenticationChecker {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationChecker.class);

    private final JurisdictionToUserMapping jurisdictionMapping;
    private final IdamClient idamClient;


    private OAuth2Configuration oauth2Configuration;


    public AuthenticationChecker(
        JurisdictionToUserMapping jurisdictionMapping,
        IdamClient idamClient,
        OAuth2Configuration oauth2Configuration
    ) {
        this.jurisdictionMapping = jurisdictionMapping;
        this.idamClient = idamClient;
        this.oauth2Configuration = oauth2Configuration;
    }

    public List<JurisdictionConfigurationStatus> checkSignInForAllJurisdictions() {
        return jurisdictionMapping.getUsers()
            .entrySet()
            .stream()
            .map(entry -> checkSignIn(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    public JurisdictionConfigurationStatus checkSignInForJurisdiction(String jurisdiction) {
        try {
            return checkSignIn(jurisdiction, jurisdictionMapping.getUser(jurisdiction));
        } catch (NoUserConfiguredException exception) {
            return new JurisdictionConfigurationStatus(jurisdiction, false, exception.getMessage(), null);
        }
    }

    private JurisdictionConfigurationStatus checkSignIn(String jurisdiction, Credential credential) {
        try {

            String clientId = oauth2Configuration.getClientId();

            String redirectUri = oauth2Configuration.getRedirectUri();

            log.info("clientId {}, redirectUri {}, jurisdiction {}, getUsername {}, getPassword {}",
                    clientId, redirectUri, jurisdiction, credential.getUsername(), credential.getPassword());

            idamClient.authenticateUser(credential.getUsername(), credential.getPassword());

            log.info("Successful authentication of {} jurisdiction", jurisdiction);

            return new JurisdictionConfigurationStatus(jurisdiction, true);
        } catch (FeignException e) {
            log.error(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                e
            );

            // temp fix until new version of idam-client is released
            String desc = e.content() != null ? e.contentUTF8() : e.getMessage();
            return new JurisdictionConfigurationStatus(jurisdiction, false, desc, e.status());
        } catch (Exception e) {
            log.error(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                e
            );

            return new JurisdictionConfigurationStatus(jurisdiction, false, e.getMessage(), null);
        }
    }
}
