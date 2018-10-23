package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;

@RunWith(MockitoJUnitRunner.class)
public class ReceiverFactoryTest {

    @Test
    public void create_should_return_client_from_client_factory() {

        String connString =
            "Endpoint=sb://test123.example.com/;SharedAccessKeyName=test;SharedAccessKey=testKey;EntityPath=envelopes";

        Throwable thrown = catchThrowable(() -> new ReceiverFactory(connString).create());

        // when
        assertThat(thrown).isInstanceOf(ConnectionException.class);
    }
}
