package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import javax.validation.constraints.NotEmpty;

@ConfigurationProperties(prefix = "service-config")
@Validated
public class ServiceConfiguration {
    @NotEmpty
    private List<ServiceConfigItem> services;

    public void setServices(List<ServiceConfigItem> services) {
        this.services = services;
    }

    public List<ServiceConfigItem> getServices() {
        return services;
    }
}
