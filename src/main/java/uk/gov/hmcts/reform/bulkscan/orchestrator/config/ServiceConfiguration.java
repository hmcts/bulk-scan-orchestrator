package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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
