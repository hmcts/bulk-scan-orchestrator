package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigProvider.ServiceConfiguration;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ServiceConfigProviderTest {

    @Test
    public void getConfig_should_return_the_right_service_configuration_when_present() {
        // given
        ServiceConfigItem service1Config =
            serviceConfigItem("service1", "jurisdiction1", Arrays.asList("ctid1", "ctid2"));

        ServiceConfigItem service2Config =
            serviceConfigItem("service2", "jurisdiction2", Arrays.asList("ctid1", "ctid3"));

        List<ServiceConfigItem> configuredServices = Arrays.asList(service1Config, service2Config);

        // when
        ServiceConfigItem configItem = serviceConfigProvider(configuredServices).getConfig("service2");

        // then
        assertThat(configItem).isEqualToComparingFieldByField(service2Config);
    }

    @Test
    public void getConfig_should_throw_exception_when_service_is_not_configured() {
        ServiceConfigProvider serviceConfigProvider = serviceConfigProvider(
            Arrays.asList(
                serviceConfigItem("service", "jurisdiction", singletonList("ctid"))
            )
        );

        assertThatThrownBy(
            () -> serviceConfigProvider.getConfig("non-existing-service")
        )
            .isInstanceOf(ServiceNotConfiguredException.class)
            .hasMessage("Service non-existing-service is not configured");
    }

    private ServiceConfigProvider serviceConfigProvider(List<ServiceConfigItem> services) {
        ServiceConfiguration serviceConfiguration = new ServiceConfiguration();
        serviceConfiguration.setServices(services);
        return new ServiceConfigProvider(serviceConfiguration);
    }

    private ServiceConfigItem serviceConfigItem(String service, String jurisdiction, List<String> caseTypeIds) {
        ServiceConfigItem serviceConfigItem = new ServiceConfigItem();
        serviceConfigItem.setService(service);
        serviceConfigItem.setJurisdiction(jurisdiction);
        serviceConfigItem.setCaseTypeIds(caseTypeIds);
        return serviceConfigItem;
    }
}
