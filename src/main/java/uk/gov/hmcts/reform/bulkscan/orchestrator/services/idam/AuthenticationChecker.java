package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints.IdamConfigStatusEndpoint;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class AuthenticationChecker {

    private static final Logger log = LoggerFactory.getLogger(IdamConfigStatusEndpoint.class);

    private final JurisdictionToUserMapping jurisdictionMapping;
    private final IdamClient idamClient;

    public AuthenticationChecker(
        JurisdictionToUserMapping jurisdictionMapping,
        IdamClient idamClient
    ) {
        this.jurisdictionMapping = jurisdictionMapping;
        this.idamClient = idamClient;
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
            idamClient.authenticateUser(credential.getUsername(), credential.getPassword());

            log.debug("Successful authentication of {} jurisdiction", jurisdiction);

            return new JurisdictionConfigurationStatus(jurisdiction, true);
        } catch (FeignException e) {
            log.warn(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                e
            );

            return new JurisdictionConfigurationStatus(jurisdiction, false, e.getMessage(), e.status());
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
