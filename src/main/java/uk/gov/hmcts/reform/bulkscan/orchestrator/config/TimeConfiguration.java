package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

@Configuration
public class TimeConfiguration {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    // global setting via config files not working with java8
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomiser() {
        return builder -> {
            builder.simpleDateFormat(DATE_TIME_PATTERN);

            builder.serializers(
                new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE),
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)),
                new ZonedDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN))
            );
        };
    }
}
