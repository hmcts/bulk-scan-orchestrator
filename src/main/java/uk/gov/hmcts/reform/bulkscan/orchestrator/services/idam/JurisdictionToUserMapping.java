package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.toMap;

@ConfigurationProperties(prefix = "idam")
public class JurisdictionToUserMapping {

    private static final Logger log = LoggerFactory.getLogger(JurisdictionToUserMapping.class);

    private Map<String, Credential> users = new HashMap<>();

    @Autowired
    private Environment env;

    public void setUsers(Map<String, Map<String, String>> users) {
        this.users = users
            .entrySet()
            .stream()
            .map(this::createEntry)
            .collect(toMap(Entry::getKey, Entry::getValue));
    }

    public Map<String, Credential> getUsers() {
        return users;
    }

    private Entry<String, Credential> createEntry(Entry<String, Map<String, String>> entry) {
        String key = entry.getKey().toLowerCase();
        Credential cred = new Credential(entry.getValue().get("username"), entry.getValue().get("password"));

        return new AbstractMap.SimpleEntry<>(key, cred);
    }

    public Credential getUser(String jurisdiction) {
        log.warn("US: {}", env.getProperty("IDAM_USERS_BULKSCAN_USERNAME"));
        log.warn("us: {}", env.getProperty("bulk-scan.idam-users-bulkscan-username"));
        return users.computeIfAbsent(jurisdiction.toLowerCase(), this::throwNotFound);
    }

    private Credential throwNotFound(String jurisdiction) {
        throw new NoUserConfiguredException(jurisdiction);
    }
}
