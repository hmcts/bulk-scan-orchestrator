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
            .map(this::toEntry)
            .collect(HashMap::new, (map, pair) -> map.put(pair.first().toLowerCase(), pair.second()), HashMap::putAll);
    }

    private Pair<String, Credential> toEntry(Map.Entry<String, Map<String, String>> entry) {
        String key = entry.getKey().toUpperCase();
        Credential cred = new Credential(entry.getValue().get("username"), entry.getValue().get("password"));
        return new Pair<>(key, cred);
    }

    public Credential getUser(String jurisdiction) {

        Credential credential = users.get(jurisdiction.toLowerCase());
        if (credential != null) {
            return credential;
        } else {
            throw new NoUserConfiguredException(jurisdiction);
        }
    }
}
