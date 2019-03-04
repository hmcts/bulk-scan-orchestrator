package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.out.JurisdictionConfigurationStatus;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.NoUserConfiguredException;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Endpoint(id = "idam-config-status")
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class IdamConfigStatusEndpoint {

    private static final Logger log = LoggerFactory.getLogger(IdamConfigStatusEndpoint.class);

    private final JurisdictionToUserMapping jurisdictionMapping;
    private final IdamClient idamClient;

    @Autowired
    private Environment env;

    public IdamConfigStatusEndpoint(
        JurisdictionToUserMapping mapping,
        IdamClient idamClient
    ) {
        jurisdictionMapping = mapping;
        this.idamClient = idamClient;
    }

    @ReadOperation
    public List<JurisdictionConfigurationStatus> jurisdictions() {
        if (env != null) {
            log.warn("US: {}", env.getProperty("IDAM_USERS_BULKSCAN_USERNAME"));
            log.warn("US.: {}", env.getProperty("idam.users.bulkscan.username"));
            log.warn("us: {}", env.getProperty("bulk-scan.idam-users-bulkscan-username"));
        }
        jurisdictionMapping.getUsers()
            .entrySet()
            .forEach(e -> log.warn("EK: {}; EV: {}", e.getKey(), e.getValue()));

        return jurisdictionMapping.getUsers()
            .entrySet()
            .stream()
            .map(entry -> checkCredentials(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    @ReadOperation
    public JurisdictionConfigurationStatus jurisdiction(@Selector String jurisdiction) {
        try {
            return checkCredentials(jurisdiction, jurisdictionMapping.getUser(jurisdiction));
        } catch (NoUserConfiguredException exception) {
            return new JurisdictionConfigurationStatus(jurisdiction, false, exception.getMessage());
        }
    }

    private JurisdictionConfigurationStatus checkCredentials(String jurisdiction, Credential credential) {
        try {
            log.warn("U: {}; P: {}", credential.getUsername(), credential.getPassword());
            idamClient.authenticateUser(credential.getUsername(), credential.getPassword());

            log.debug("Successful authentication of {} jurisdiction", jurisdiction);

            return new JurisdictionConfigurationStatus(jurisdiction, true);
        } catch (FeignException exception) {
            log.warn(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                exception
            );

            return new JurisdictionConfigurationStatus(jurisdiction, false, exception.getMessage());
        } catch (Exception exception) {
            log.error(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                exception
            );

            return new JurisdictionConfigurationStatus(jurisdiction, false, exception.getMessage());
        }
    }
}
