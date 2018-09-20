package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "idam")
public class JurisdictionToUserMapping {
    private Map<String, Credential> users;

    public Map<String, Credential> getUsers() {
        return users;
    }

    public void setUsers(Map<String, Credential> users) {
        this.users = users;
    }
}
