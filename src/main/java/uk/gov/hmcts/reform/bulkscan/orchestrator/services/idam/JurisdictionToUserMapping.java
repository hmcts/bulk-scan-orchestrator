package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import com.netflix.util.Pair;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "idam")
public class JurisdictionToUserMapping {

    private Map<String, Credential> users;

    public void setUsers(Map<String, Map<String, String>> users) {
        this.users = users
            .entrySet()
            .stream()
            .map(this::createPair)
            .collect(HashMap::new, (map, pair) -> map.put(pair.first(), pair.second()), HashMap::putAll);
    }

    private Pair<String, Credential> createPair(Map.Entry<String, Map<String, String>> entry) {
        String key = entry.getKey().toLowerCase();
        Credential cred = new Credential(entry.getValue().get("username"), entry.getValue().get("password"));
        return new Pair<>(key, cred);
    }

    public Credential getUser(String jurisdiction) {
        return users.computeIfAbsent(jurisdiction.toLowerCase(),this::throwNotFound);
    }

    private Credential throwNotFound(String jurisdiction) {
        throw new NoUserConfiguredException(jurisdiction);
    }
}
