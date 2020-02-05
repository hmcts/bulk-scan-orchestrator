package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ServiceConfigTest {

    private static final String TEST_SERVICE_NAME = "bulkscan";

    @Autowired
    private ServiceConfigProvider serviceConfigProvider;

    @Test
    public void serviceConfigItem_should_store_form_type_to_ocr_field_name_mappings() {
        ServiceConfigItem configItem = serviceConfigProvider.getConfig(TEST_SERVICE_NAME);

        //form types and field names are defined in application-integration.yaml
        assertThat(configItem.getSurnameOcrFieldNameList("form1").get(0)).isEqualTo("lastName");
        assertThat(configItem.getSurnameOcrFieldNameList("form2").get(1)).isEqualTo("surname");
    }

    @Test
    public void serviceConfigItem_should_store_allow_attach_exception_record_to_case_and_classification_mappings() {
        ServiceConfigItem configItem = serviceConfigProvider.getConfig(TEST_SERVICE_NAME);

        assertThat(configItem.getAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications()).isNotNull();
        assertThat(configItem.getAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications().get(0))
            .isEqualTo(Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR.toString());
    }
}
