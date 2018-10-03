package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.AbstractMap;
import java.util.Map;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.toMap;

@ConfigurationProperties(prefix = "idam")
public class JurisdictionToUserMapping {

    private Map<String, Credential> users = ImmutableMap.of();

    public void setUsers(Map<String, Map<String, String>> users) {
        this.users = users
            .entrySet()
            .stream()
            .map(this::createEntry)
            .collect(toMap(Entry::getKey, Entry::getValue));
    }

    private Entry<String, Credential> createEntry(Entry<String, Map<String, String>> entry) {
        String key = entry.getKey().toLowerCase();
        Credential cred = new Credential(entry.getValue().get("username"), entry.getValue().get("password"));

        return new AbstractMap.SimpleEntry<>(key, cred);
    }

    public Credential getUser(String jurisdiction) {
        return users.computeIfAbsent(jurisdiction.toLowerCase(), this::throwNotFound);
    }

    private Credential throwNotFound(String jurisdiction) {
        throw new NoUserConfiguredException(jurisdiction);
    }
}
