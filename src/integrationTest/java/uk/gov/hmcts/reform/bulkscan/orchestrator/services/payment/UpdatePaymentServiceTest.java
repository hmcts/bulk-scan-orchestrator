package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWireMock(port = 0)
@IntegrationTest
public class UpdatePaymentServiceTest {

    @Autowired
    UpdatePaymentService updatePaymentService;

    @Autowired
    UpdatePaymentRepository updatePaymentRepository;

    final UpdatePayment updatePayment = new UpdatePayment(
        Instant.now(),
        "exceptionRecordRef",
        "newCaseRef",
        "envelope_id",
        "jurisdiction",
        "awaiting"
    );

    @BeforeEach
    void setUp() {
        updatePaymentService = new UpdatePaymentService(updatePaymentRepository);
    }

    @Test
    void shouldSaveAndThenGetUpdatePaymentFromDatabaseByStatus() {

        updatePaymentService.savePayment(updatePayment);

        List<UpdatePayment> results =
            updatePaymentService.getUpdatePaymentByStatus("awaiting");

        assertThat(results.getFirst().status).isEqualTo("awaiting");
    }

}
