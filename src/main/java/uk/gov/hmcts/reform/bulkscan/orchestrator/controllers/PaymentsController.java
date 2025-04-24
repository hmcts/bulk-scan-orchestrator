package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;

import java.util.List;

@RestController
@Tag(name = "Payments - API for handling payments")
@RequestMapping("/payments")
public class PaymentsController {

    private final PaymentsService paymentsService;

    public PaymentsController(PaymentsService paymentsService) {
        this.paymentsService = paymentsService;
    }

    @GetMapping("/new/failed")
    @ApiResponse(responseCode = "200", description = "All new payments in a failed status returned")
    @Operation(summary = "Returns all new payments that are in a failed status")
    public ResponseEntity<List<Payment>> handleNewPaymentFailure() {
        return ResponseEntity.ok(paymentsService.getAllFailedNewPayments());
    }

    @GetMapping("/updated/failed")
    @ApiResponse(responseCode = "200", description = "All updated payments in a failed status returned")
    @Operation(summary = "Returns all updated payments in a failed status")
    public ResponseEntity<List<UpdatePayment>> handleUpdatedPaymentFailure() {
        return ResponseEntity.ok(paymentsService.getAllFailedUpdatePayments());
    }

    @PutMapping("/new/retry/{id}")
    @ApiResponse(responseCode = "200", description = "Reprocess successful, returns updated entity")
    @ApiResponse(responseCode = "404", description = "Payment by id not found")
    @ApiResponse(responseCode = "422", description = "Payment failed reprocessing")
    @Operation(summary = "Retry a failed new payment")
    public ResponseEntity<Payment> retryNewPayment(@PathVariable("id") String paymentId) {
        return ResponseEntity.ok(paymentsService.reprocessNewPayment(paymentId));
    }

    @PutMapping("/updated/retry/{id}")
    @ApiResponse(responseCode = "200", description = "Reprocess successful, returns updated entity")
    @ApiResponse(responseCode = "404", description = "Payment by id not found")
    @ApiResponse(responseCode = "422", description = "Payment failed reprocessing")
    @Operation(summary = "Retry a failed updated payment")
    public ResponseEntity<UpdatePayment> retryUpdatedPayment(@PathVariable("id") String paymentId) {
        return ResponseEntity.ok(paymentsService.reprocessUpdatePayment(paymentId));
    }
}
