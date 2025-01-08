package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;


public final class CsvWriter {

    private static final String[] CREATE_PAYMENT_CSV_HEADERS = {
        "Envelope Id", "Date Received", "CCD Reference",
        "Service", "Exception Record", "Status"
    };

    private static final String[] UPDATE_PAYMENT_CSV_HEADERS = {
        "Exception Record Ref",  "Date Received", "New Case Ref",
        "Envelope Id", "Jurisdiction", "Status"
    };


    private CsvWriter() {
        // utility class constructor
    }

    public static File writeCreatePaymentsToCsv(
        List<Payment> data
    ) throws IOException {
        File csvFile = File.createTempFile("CreatePayment-summary-", ".csv");

        CSVFormat csvFileHeader = CSVFormat.DEFAULT.withHeader(CREATE_PAYMENT_CSV_HEADERS);
        try (
            FileWriter fileWriter = new FileWriter(csvFile);
            CSVPrinter printer = new CSVPrinter(fileWriter, csvFileHeader)
        ) {
            for (Payment payment : ofNullable(data).orElse(emptyList())) {
                printer.printRecord(
                    payment.envelopeId,
                    payment.createdAt,
                    payment.ccdReference,
                    payment.service,
                    payment.isExceptionRecord,
                    payment.status
                );
            }
        }
        return csvFile;
    }

    public static File writeUpdatePaymentsToCsv(
        List<UpdatePayment> data
    ) throws IOException {
        File csvFile = File.createTempFile("UpdatePayment-summary-", ".csv");

        CSVFormat csvFileHeader = CSVFormat.DEFAULT.withHeader(UPDATE_PAYMENT_CSV_HEADERS);
        try (
            FileWriter fileWriter = new FileWriter(csvFile);
            CSVPrinter printer = new CSVPrinter(fileWriter, csvFileHeader)
        ) {
            for (UpdatePayment updatePayment : ofNullable(data).orElse(emptyList())) {
                printer.printRecord(
                    updatePayment.exceptionRecordRef,
                    updatePayment.createdAt,
                    updatePayment.newCaseRef,
                    updatePayment.envelopeId,
                    updatePayment.jurisdiction,
                    updatePayment.status
                );
            }
        }
        return csvFile;
    }
}

