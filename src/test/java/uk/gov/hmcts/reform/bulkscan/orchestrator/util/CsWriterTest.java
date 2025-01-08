package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

public class CsWriterTest {

    @Test
    void should_return_csv_file_with_headers_and_csv_records_for_create_payments() throws IOException {

        final PaymentData paymentData = new PaymentData("123");

        final Instant date = Instant.now();

        //given
        List<Payment> paymentList = Arrays.asList(
            new Payment(
                "123",
                date,
                "234",
                "jurisdiction1",
                "service1",
                "poBox1",
                true,
                "awaiting",
                Collections.singletonList(paymentData)
            ),
            new Payment(
                "567",
                date,
                "789",
                "jurisdiction1",
                "service2",
                "poBox1",
                true,
                "awaiting",
                Collections.singletonList(paymentData)
            )
        );

        //when
        File paymentsToCsv = CsvWriter.writeCreatePaymentsToCsv(paymentList);

        //then
        List<CSVRecord> csvRecordList = readCsv(paymentsToCsv);


        assertThat(csvRecordList)
            .isNotEmpty()
            .hasSize(3)
            .extracting(data -> tuple(
                data.get(0),
                data.get(1),
                data.get(2),
                data.get(3),
                data.get(4),
                data.get(5))
            )
            .containsExactly(
                tuple(
                    "Envelope Id",
                    "Date Received",
                    "CCD Reference",
                    "Service",
                    "Exception Record",
                    "Status"
                ),
                tuple(
                    "123",
                    date.toString(),
                    "234",
                    "service1",
                    "true",
                    "awaiting"
                ),
                tuple(
                    "567",
                    date.toString(),
                    "789",
                    "service2",
                    "true",
                    "awaiting"
                )
            );
    }


    @Test
    void should_return_csv_file_with_headers_and_csv_records_for_update_payments() throws IOException {


        final Instant date = Instant.now();

        //given
        List<UpdatePayment> paymentList = Arrays.asList(
            new UpdatePayment(
                date,
                "123",
                "234",
                "123456",
                "jurisdiction1",
                "awaiting"
            ),
            new UpdatePayment(
                date,
                "456",
                "789",
                "654321",
                "jurisdiction2",
                "success"
            )
        );

        //when
        File paymentsToCsv = CsvWriter.writeUpdatePaymentsToCsv(paymentList);

        //then
        List<CSVRecord> csvRecordList = readCsv(paymentsToCsv);


        assertThat(csvRecordList)
            .isNotEmpty()
            .hasSize(3)
            .extracting(data -> tuple(
                data.get(0),
                data.get(1),
                data.get(2),
                data.get(3),
                data.get(4),
                data.get(5))
            )
            .containsExactly(
                tuple(
                    "Exception Record Ref",
                    "Date Received",
                    "New Case Ref",
                    "Envelope Id",
                    "Jurisdiction",
                    "Status"
                ),
                tuple(
                    "123",
                    date.toString(),
                    "234",
                    "123456",
                    "jurisdiction1",
                    "awaiting"
                ),
                tuple(
                    "456",
                    date.toString(),
                    "789",
                    "654321",
                    "jurisdiction2",
                    "success"
                )
            );
    }


    private List<CSVRecord> readCsv(File toCsv) throws IOException {
        return CSVFormat.DEFAULT.parse(new FileReader(toCsv)).getRecords();
    }
}
