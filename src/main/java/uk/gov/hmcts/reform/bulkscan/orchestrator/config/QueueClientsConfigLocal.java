package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.time.Duration;
import java.util.Hashtable;

@Configuration
@Profile("nosb") // do not register handler for the nosb (test) profile
public class QueueClientsConfigLocal {

    ConnectionStringBuilder csb = new ConnectionStringBuilder("SECRET CONNECTION STRING");

    @Bean
    public ConnectionFactory connectionFactory() throws JMSException {
//        ConnectionFactory jmsConnectionFactory = new JmsConnectionFactory("amqps://localhost:5672"); that's connect to local RABBIT

        // that's connect to SB if provided secret is ConnectionString is correct
        ConnectionFactory jmsConnectionFactory = new JmsConnectionFactory("amqps://" + csb.getEndpoint().getHost() + "?amqp.idleTimeout=120000&amqp.traceFrames=true");

        return jmsConnectionFactory;
    }
    @Bean("envelopes")
    public MessageConsumer envelopesMessageReceiver(
        Connection connection
    ) throws JMSException, NamingException {
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        Hashtable<String, String> hashtable = new Hashtable<>();
        hashtable.put("queue.ENVELOP", "envelopes-test");
        hashtable.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");

        InitialContext initialContext = new InitialContext(hashtable);
        Destination envelopes = (Destination) initialContext.lookup("ENVELOP");
        return session.createConsumer(envelopes);
    }

    @Bean("processed-envelopes") // TODO client code, that would need to be replaced with MessageProducer
    public QueueClient processedEnvelopesQueueClient() {
        return null;
    }

    @Bean
    public Connection connection(ConnectionFactory factory) throws JMSException {
        Connection connection = factory.createConnection(csb.getSasKeyName(), csb.getSasKey());
        connection.start();
        return connection;
    }

    @Bean("payments-queue-config")
    protected QueueConfigurationProperties paymentsQueueConfig() {
        return null;
    }

    @Bean("payments")
    public QueueClient paymentsQueueClient() {
        return null;
    }
}
