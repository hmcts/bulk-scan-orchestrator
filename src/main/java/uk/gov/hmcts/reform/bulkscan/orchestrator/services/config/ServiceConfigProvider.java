package uk.gov.hmcts.reform.bulkscan.orchestrator.services.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfiguration;

import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Service
@EnableConfigurationProperties(ServiceConfiguration.class)
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

        if (configItem == null) {
            throw new ServiceNotConfiguredException(String.format("Service %s is not configured", service));
        } else {
            return configItem;
        }
    }

}
