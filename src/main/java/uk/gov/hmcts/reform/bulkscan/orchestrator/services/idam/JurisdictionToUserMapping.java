package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import com.microsoft.applicationinsights.core.dependencies.googlecommon.collect.ImmutableMap;
import com.netflix.util.Pair;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

@ConfigurationProperties(prefix = "idam")
public class JurisdictionToUserMapping {

    private Map<String, Credential> users = ImmutableMap.of();

    public void setUsers(Map<String, Map<String, String>> users) {
        this.users = users
            .entrySet()
            .stream()
            .map(this::createPair)
            .collect(toMap(Pair::first, Pair::second));
    }

    private Pair<String, Credential> createPair(Map.Entry<String, Map<String, String>> entry) {
        String key = entry.getKey().toLowerCase();
        Credential cred = new Credential(entry.getValue().get("username"), entry.getValue().get("password"));
        return new Pair<>(key, cred);
    }

    public Credential getUser(String jurisdiction) {
        return users.computeIfAbsent(jurisdiction.toLowerCase(), this::throwNotFound);
    }

    private Credential throwNotFound(String jurisdiction) {
        throw new NoUserConfiguredException(jurisdiction);
    }
}
