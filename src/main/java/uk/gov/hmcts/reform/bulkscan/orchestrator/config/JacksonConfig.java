package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register the module that supports Java 8 time types (like LocalDateTime)
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
