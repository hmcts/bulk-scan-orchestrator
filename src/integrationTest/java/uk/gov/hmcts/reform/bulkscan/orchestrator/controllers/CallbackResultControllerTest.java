package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.CallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackResultService;

import java.time.Instant;
import java.util.Calendar;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.ATTACH_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.RequestType.CREATE_CASE;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CallbackResultController.class)
public class CallbackResultControllerTest {

    private static final String CASE_ID1 = "CASE_ID1";
    private static final String CASE_ID2 = "CASE_ID2";
    private static final String EXCEPTION_RECORD_ID1 = "ER_ID1";
    private static final String EXCEPTION_RECORD_ID2 = "ER_ID2";
    private static final String CREATED_AT2 = "2021-05-05T01:36:22.727Z";
    private static final String CREATED_AT1 = "2021-05-05T01:35:22.727Z";
    private static final Instant TIMESTAMP1;
    private static final Instant TIMESTAMP2;

    static {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2021);
        cal.set(Calendar.MONTH, 4);
        cal.set(Calendar.DAY_OF_MONTH, 4);
        cal.set(Calendar.HOUR, 14);
        cal.set(Calendar.MINUTE, 35);
        cal.set(Calendar.SECOND, 22);
        cal.set(Calendar.MILLISECOND, 727);
        TIMESTAMP1 = Instant.ofEpochMilli(cal.getTimeInMillis());
        cal.set(Calendar.MINUTE, 36);
        TIMESTAMP2 = Instant.ofEpochMilli(cal.getTimeInMillis());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CallbackResultService callbackResultService;

    @Test
    void should_return_callback_result_by_case_id() throws Exception {
        UUID id = UUID.randomUUID();
        given(callbackResultService.findByCaseId(CASE_ID1))
            .willReturn(singletonList(
                new CallbackResult(id, TIMESTAMP1, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1)
            ));

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("case_id", CASE_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.callback-results", hasSize(1)))
            .andExpect(jsonPath("$.callback-results[0].id").value(id.toString()))
            .andExpect(jsonPath("$.callback-results[0].created_at").value(CREATED_AT1))
            .andExpect(jsonPath("$.callback-results[0].request_type").value(CREATE_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[0].exception_record_id").value(EXCEPTION_RECORD_ID1))
            .andExpect(jsonPath("$.callback-results[0].case_id").value(CASE_ID1));
    }

    @Test
    void should_return_multiple_callback_results_by_case_id() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        given(callbackResultService.findByCaseId(CASE_ID1))
            .willReturn(asList(
                new CallbackResult(id1, TIMESTAMP1, ATTACH_TO_CASE, EXCEPTION_RECORD_ID1, CASE_ID1),
                new CallbackResult(id2, TIMESTAMP2, ATTACH_TO_CASE, EXCEPTION_RECORD_ID2, CASE_ID1)
            ));

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("case_id", CASE_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.callback-results", hasSize(2)))
            .andExpect(jsonPath("$.callback-results[0].id").value(id1.toString()))
            .andExpect(jsonPath("$.callback-results[0].created_at").value(CREATED_AT1))
            .andExpect(jsonPath("$.callback-results[0].request_type").value(ATTACH_TO_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[0].exception_record_id").value(EXCEPTION_RECORD_ID1))
            .andExpect(jsonPath("$.callback-results[0].case_id").value(CASE_ID1))
            .andExpect(jsonPath("$.callback-results[1].id").value(id2.toString()))
            .andExpect(jsonPath("$.callback-results[1].created_at").value(CREATED_AT2))
            .andExpect(jsonPath("$.callback-results[1].request_type").value(ATTACH_TO_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[1].exception_record_id").value(EXCEPTION_RECORD_ID2))
            .andExpect(jsonPath("$.callback-results[1].case_id").value(CASE_ID1));
    }

    @Test
    void should_return_empty_result_by_case_id_if_no_callback_results_found() throws Exception {
        given(callbackResultService.findByCaseId(CASE_ID1)).willReturn(emptyList());

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("case_id", CASE_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.callback-results", hasSize(0)));
    }

    @Test
    void should_return_callback_result_by_exception_record_id() throws Exception {
        UUID id = UUID.randomUUID();
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1))
            .willReturn(singletonList(
                new CallbackResult(id, TIMESTAMP1, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1)
            ));

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("exception_record_id", EXCEPTION_RECORD_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.callback-results", hasSize(1)))
            .andExpect(jsonPath("$.callback-results[0].id").value(id.toString()))
            .andExpect(jsonPath("$.callback-results[0].created_at").value(CREATED_AT1))
            .andExpect(jsonPath("$.callback-results[0].request_type").value(CREATE_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[0].exception_record_id").value(EXCEPTION_RECORD_ID1))
            .andExpect(jsonPath("$.callback-results[0].case_id").value(CASE_ID1));
    }

    @Test
    void should_return_multiple_callback_results_by_exception_record_id() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1))
            .willReturn(asList(
                new CallbackResult(id1, TIMESTAMP1, CREATE_CASE, EXCEPTION_RECORD_ID1, CASE_ID1),
                new CallbackResult(id2, TIMESTAMP2, ATTACH_TO_CASE, EXCEPTION_RECORD_ID1, CASE_ID2)
            ));

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("exception_record_id", EXCEPTION_RECORD_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.callback-results", hasSize(2)))
            .andExpect(jsonPath("$.callback-results[0].id").value(id1.toString()))
            .andExpect(jsonPath("$.callback-results[0].created_at").value(CREATED_AT1))
            .andExpect(jsonPath("$.callback-results[0].request_type").value(CREATE_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[0].exception_record_id").value(EXCEPTION_RECORD_ID1))
            .andExpect(jsonPath("$.callback-results[0].case_id").value(CASE_ID1))
            .andExpect(jsonPath("$.callback-results[1].id").value(id2.toString()))
            .andExpect(jsonPath("$.callback-results[1].created_at").value(CREATED_AT2))
            .andExpect(jsonPath("$.callback-results[1].request_type").value(ATTACH_TO_CASE.toString()))
            .andExpect(jsonPath("$.callback-results[1].exception_record_id").value(EXCEPTION_RECORD_ID1))
            .andExpect(jsonPath("$.callback-results[1].case_id").value(CASE_ID2));
    }

    @Test
    void should_return_empty_result_by_exception_record_id_if_no_callback_results_found() throws Exception {
        given(callbackResultService.findByExceptionRecordId(EXCEPTION_RECORD_ID1)).willReturn(emptyList());

        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("exception_record_id", EXCEPTION_RECORD_ID1)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.callback-results", hasSize(0)));
    }

    @Test
    void should_return_bad_request_if_both_parameters_are_present() throws Exception {
        mockMvc
            .perform(
                get("/callback-results")
                    .queryParam("case_id", CASE_ID1)
                    .queryParam("exception_record_id", EXCEPTION_RECORD_ID1)
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Request should have exactly one parameter 'case_id' or 'exception_record_id'"));
    }

    @Test
    void should_return_bad_request_if_no_parameter_is_present() throws Exception {
        mockMvc
            .perform(
                get("/callback-results")
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Request should have exactly one parameter 'case_id' or 'exception_record_id'"));
    }
}
