package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Service
@EnableConfigurationProperties(ServiceConfigProvider.ServiceConfiguration.class)
public class ServiceConfigProvider {

    private final Map<String, ServiceConfigItem> servicesByName;

    public ServiceConfigProvider(ServiceConfiguration serviceConfiguration) {
        this.servicesByName =
            serviceConfiguration
                .getServices()
                .stream()
                .collect(
                    toMap(
                        ServiceConfigItem::getService,
                        identity()
                    )
                );
    }

    public ServiceConfigItem getConfig(String service) {
        ServiceConfigItem configItem = servicesByName.get(service);

        if (configItem != null) {
            return configItem;
        } else {
            throw new ServiceNotConfiguredException(String.format("Service %s is not configured", service));
        }
    }

    @ConfigurationProperties(prefix = "service-config")
    @Validated
    public static class ServiceConfiguration {
        @NotEmpty
        private List<ServiceConfigItem> services;

        public void setServices(List<ServiceConfigItem> services) {
            this.services = services;
        }

        public List<ServiceConfigItem> getServices() {
            return services;
        }
    }
}
