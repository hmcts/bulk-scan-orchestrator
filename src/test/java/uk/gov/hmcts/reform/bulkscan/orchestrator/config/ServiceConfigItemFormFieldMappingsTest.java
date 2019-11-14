package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceConfigItemFormFieldMappingsTest {

    private static final String FORM_1 = "form1";
    private static final String FIELD_1 = "field1";
    private static final String FORM_2 = "form2";
    private static final String FIELD_2 = "field2";
    private static final String FIELD_3 = "field3";

    private ServiceConfigItem configItem;

    @BeforeEach
    public void setUp() {
        configItem = new ServiceConfigItem();
    }

    @Test
    void should_return_null_if_mappings_not_set() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrField(FIELD_1);

        // when
        final String fieldName1 = configItem.getSurnameOcrFieldName(FORM_1);

        // then
        assertThat(fieldName1).isEqualTo(null);
    }

    @Test
    void should_return_null_if_no_mapping_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrField(FIELD_1);

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1));

        // when
        final String fieldName1 = configItem.getSurnameOcrFieldName(FORM_2);

        // then
        assertThat(fieldName1).isEqualTo(null);
    }

    @Test
    void should_return_single_ocr_field_name_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrField(FIELD_1);

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1));

        // when
        final String fieldName1 = configItem.getSurnameOcrFieldName(FORM_1);

        // then
        assertThat(fieldName1).isEqualTo(FIELD_1);
    }

    @Test
    void should_return_two_ocr_field_names_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrField(FIELD_1);
        FormFieldMapping formFieldMapping2 = new FormFieldMapping();
        formFieldMapping2.setFormType(FORM_2);
        formFieldMapping2.setOcrField(FIELD_2);

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1, formFieldMapping2));

        // when
        final String fieldName1 = configItem.getSurnameOcrFieldName(FORM_1);
        final String fieldName2 = configItem.getSurnameOcrFieldName(FORM_2);

        // then
        assertThat(fieldName1).isEqualTo(FIELD_1);
        assertThat(fieldName2).isEqualTo(FIELD_2);
    }

    @Test
    void should_handle_multiple_ocr_field_names_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrField(FIELD_1);
        FormFieldMapping formFieldMapping2 = new FormFieldMapping();
        formFieldMapping2.setFormType(FORM_2);
        formFieldMapping2.setOcrField(FIELD_2);
        FormFieldMapping formFieldMapping3 = new FormFieldMapping();
        formFieldMapping3.setFormType(FORM_2);
        formFieldMapping3.setOcrField(FIELD_3);

        configItem.setFormTypeToSurnameOcrFieldMappings(
            asList(formFieldMapping1, formFieldMapping2, formFieldMapping3)
        );

        // when
        final String fieldName1 = configItem.getSurnameOcrFieldName(FORM_1);
        final String fieldName2 = configItem.getSurnameOcrFieldName(FORM_2);

        // then
        assertThat(fieldName1).isEqualTo(FIELD_1);
        assertThat(fieldName2).isEqualTo(FIELD_2);
    }
}
