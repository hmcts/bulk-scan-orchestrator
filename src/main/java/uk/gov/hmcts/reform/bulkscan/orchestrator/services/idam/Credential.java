package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties()
public class Credential {

    private final String username;
    private final String password;

    public Credential(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }
//    public void setUsername(String username){
//        this.username = username;
//    }

    public String getPassword() {
        return password;
    }
//    public void setPassword(String password){
//        this.password = password;
//    }
}
