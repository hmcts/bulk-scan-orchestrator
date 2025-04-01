package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.SendPaymentReportService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.InvalidApiKeyException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"SMTP_HOST=true"})
@WebMvcTest(SendReportController.class)
@AutoConfigureMockMvc
public class SendReportControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    SendPaymentReportService sendPaymentReportService;

    @Test
    public void should_send_daily_report() throws Exception {
        try (MockedStatic<Util> mockedStatic = mockStatic(Util.class)) {
            this.mockMvc.perform(post("/reports/email-daily-report").header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
                .andDo(print()).andExpect(status().isOk());
            verify(sendPaymentReportService, times(1)).send();
        }
    }

    @Test
    public void should_send_report_by_date() throws Exception {
        try (MockedStatic<Util> mockedStatic = mockStatic(Util.class)) {
            this.mockMvc.perform(post("/reports/email-report?date=2025-04-03")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer valid"))
                .andDo(print()).andExpect(status().isOk());
            verify(sendPaymentReportService, times(1)).send(LocalDate.of(2025, 4, 3));
        }
    }

    @Test
    public void should_not_send_daily_report_when_authorisation_not_valid() throws Exception {
        try (MockedStatic<Util> mockedStatic = mockStatic(Util.class)) {
            mockedStatic.when(() -> Util.validateAuthorization(eq("Bearer not-valid"), any())).thenThrow(
                new InvalidApiKeyException("API Key is missing")
            );
            this.mockMvc.perform(post("/reports/email-daily-report")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-valid"))
                .andDo(print()).andExpect(status().isUnauthorized())
                .andExpect(result -> Assertions.assertInstanceOf(
                    InvalidApiKeyException.class,
                    result.getResolvedException()
                ));

            verify(sendPaymentReportService, never()).send();
        }
    }

    @Test
    public void should_not_send_daily_report_by_date_when_authorisation_not_valid() throws Exception {
        try (MockedStatic<Util> mockedStatic = mockStatic(Util.class)) {
            mockedStatic.when(() -> Util.validateAuthorization(eq("Bearer not-valid"), any())).thenThrow(
                new InvalidApiKeyException("Invalid API Key")
            );
            this.mockMvc.perform(post("/reports/email-report?date=2025-04-03")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer not-valid"))
                .andDo(print()).andExpect(status().isUnauthorized())
                .andExpect(result -> Assertions.assertInstanceOf(
                    InvalidApiKeyException.class,
                    result.getResolvedException()
                ));

            verify(sendPaymentReportService, never()).send(any(LocalDate.class));
        }
    }
}
