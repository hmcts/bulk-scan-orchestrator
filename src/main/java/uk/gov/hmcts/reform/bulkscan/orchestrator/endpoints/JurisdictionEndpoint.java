package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.NoUserConfiguredException;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.Entry;

@Component
@Endpoint(id = "jurisdictions")
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class JurisdictionEndpoint {

    private static final Logger log = LoggerFactory.getLogger(JurisdictionEndpoint.class);

    private final JurisdictionToUserMapping jurisdictionMapping;
    private final IdamClient idamClient;

    public JurisdictionEndpoint(
        JurisdictionToUserMapping mapping,
        IdamClient idamClient
    ) {
        jurisdictionMapping = mapping;
        this.idamClient = idamClient;
    }

    @ReadOperation
    public Map<String, HttpStatus> jurisdictions() {
        return jurisdictionMapping.getUsers()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Entry::getKey,
                entry -> checkCredentials(entry.getKey(), entry.getValue())
            ));
    }

    @ReadOperation
    public HttpStatus jurisdiction(@Selector String jurisdiction) {
        try {
            return checkCredentials(jurisdiction, jurisdictionMapping.getUser(jurisdiction));
        } catch (NoUserConfiguredException exception) {
            return HttpStatus.UNAUTHORIZED;
        }
    }

    private HttpStatus checkCredentials(String jurisdiction, Credential credential) {
        try {
            idamClient.authenticateUser(credential.getUsername(), credential.getPassword());

            log.debug("Successful authentication");

            return HttpStatus.OK;
        } catch (FeignException exception) {
            log.warn(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                exception
            );

            // in current state unable to set response code <200 to test such situation
            // but running manually received `0` status code which crashed the endpoint response itself
            return Optional.ofNullable(HttpStatus.resolve(exception.status())).orElse(HttpStatus.UNAUTHORIZED);
        } catch (Exception exception) {
            log.error(
                "An error occurred while authenticating {} jurisdiction with {} username",
                jurisdiction,
                credential.getUsername(),
                exception
            );

            return HttpStatus.UNAUTHORIZED;
        }
    }
}
