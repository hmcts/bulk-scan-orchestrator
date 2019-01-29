package uk.gov.hmcts.reform.bulkscan.orchestrator.endpoints;

import feign.FeignException;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Map.Entry;

@Component
@Endpoint(id = "jurisdictions")
@EnableConfigurationProperties(JurisdictionToUserMapping.class)
public class JurisdictionEndpoint {

    private final Map<String, Credential> jurisdictions;
    private final Function<String, Credential> credentialProvider;
    private final IdamClient idamClient;

    public JurisdictionEndpoint(
        JurisdictionToUserMapping mapping,
        IdamClient idamClient
    ) {
        jurisdictions = mapping.getUsers();
        credentialProvider = mapping::getUser;
        this.idamClient = idamClient;
    }

    @ReadOperation
    public Map<String, HttpStatus> jurisdictions() {
        return jurisdictions
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Entry::getKey,
                entry -> checkCredentials(entry.getValue())
            ));
    }

    @ReadOperation
    public HttpStatus jurisdiction(@Selector String jurisdiction) {
        try {
            return checkCredentials(credentialProvider.apply(jurisdiction));
        } catch (NoUserConfiguredException exception) {
            return HttpStatus.UNAUTHORIZED;
        }
    }

    private HttpStatus checkCredentials(Credential credential) {
        try {
            idamClient.authenticateUser(credential.getUsername(), credential.getPassword());

            return HttpStatus.OK;
        } catch (FeignException exception) {
            return HttpStatus.valueOf(exception.status());
        } catch (Exception exception) {
            return HttpStatus.UNAUTHORIZED;
        }
    }
}
