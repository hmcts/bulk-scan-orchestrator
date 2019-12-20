package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void should_return_empty_option_if_mappings_not_set() {
        // given

        // when
        final Optional<List<String>> fieldNameList1 = configItem.getSurnameOcrFieldNameList(FORM_1);

        // then

        assertThat(fieldNameList1.isPresent()).isFalse();
    }

    @Test
    void should_return_empty_option_if_no_mapping_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrFieldList(asList(FIELD_1));

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1));

        // when
        final Optional<List<String>> fieldNameList1 = configItem.getSurnameOcrFieldNameList(FORM_2);

        // then
        assertThat(fieldNameList1.isPresent()).isFalse();
    }

    @Test
    void should_return_single_ocr_field_name_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrFieldList(asList(FIELD_1));

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1));

        // when
        final Optional<List<String>> fieldNameList1 = configItem.getSurnameOcrFieldNameList(FORM_1);

        // then
        assertThat(fieldNameList1.get().get(0)).isEqualTo(FIELD_1);
        assertThat(fieldNameList1.get().size()).isEqualTo(1);

    }

    @Test
    void should_return_two_ocr_field_name_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrFieldList(asList(FIELD_1, FIELD_2));

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1));

        // when
        final Optional<List<String>> fieldNameList1 = configItem.getSurnameOcrFieldNameList(FORM_1);

        // then
        assertThat(fieldNameList1.get().get(0)).isEqualTo(FIELD_1);
        assertThat(fieldNameList1.get().get(1)).isEqualTo(FIELD_2);
        assertThat(fieldNameList1.get().size()).isEqualTo(2);

    }

    @Test
    void should_return_two_ocr_field_names_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrFieldList(asList(FIELD_1));
        FormFieldMapping formFieldMapping2 = new FormFieldMapping();
        formFieldMapping2.setFormType(FORM_2);
        formFieldMapping2.setOcrFieldList(asList(FIELD_2));

        configItem.setFormTypeToSurnameOcrFieldMappings(asList(formFieldMapping1, formFieldMapping2));

        // when
        final Optional<List<String>> fieldNameList1 = configItem.getSurnameOcrFieldNameList(FORM_1);
        final Optional<List<String>> fieldNameList2 = configItem.getSurnameOcrFieldNameList(FORM_2);

        // then
        assertThat(fieldNameList1.get().get(0)).isEqualTo(FIELD_1);
        assertThat(fieldNameList1.get().size()).isEqualTo(1);
        assertThat(fieldNameList2.get().get(0)).isEqualTo(FIELD_2);
        assertThat(fieldNameList2.get().size()).isEqualTo(1);

    }

    @Test
    void should_fail_on_multiple_ocr_field_names_for_form_type() {
        // given
        FormFieldMapping formFieldMapping1 = new FormFieldMapping();
        formFieldMapping1.setFormType(FORM_1);
        formFieldMapping1.setOcrFieldList(asList(FIELD_1));
        FormFieldMapping formFieldMapping2 = new FormFieldMapping();
        formFieldMapping2.setFormType(FORM_2);
        formFieldMapping2.setOcrFieldList(asList(FIELD_2));
        FormFieldMapping formFieldMapping3 = new FormFieldMapping();
        formFieldMapping3.setFormType(FORM_2);
        formFieldMapping3.setOcrFieldList(asList(FIELD_3));

        // when
        assertThatThrownBy(
            () -> configItem.setFormTypeToSurnameOcrFieldMappings(
                asList(formFieldMapping1, formFieldMapping2, formFieldMapping3)
            )
        ).hasMessage("Form type has multiple mappings to surname fields [field2], [field3].");
    }
}
