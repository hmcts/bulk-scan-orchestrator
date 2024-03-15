package uk.gov.hmcts.reform.bulkscan.orchestrator.config.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

@Configuration
@EnableJms
@ConditionalOnProperty(name = "jms.enabled", havingValue = "true")
public class JmsConfiguration {

    @Value("${jms.namespace}")
    private String namespace;

    @Value("${jms.username}")
    private String username;

    @Value("${jms.password}")
    private String password;

    @Value("${jms.receiveTimeout}")
    private Long receiveTimeout;

    @Value("${jms.idleTimeout}")
    private Long idleTimeout;

    @Value("${jms.amqp-connection-string-template}")
    public String amqpConnectionStringTemplate;

    @Bean
    public ConnectionFactory orchestratorJmsConnectionFactory(@Value("${jms.application-name}")
                                                               final String clientId) {
        String connection = String.format(amqpConnectionStringTemplate, namespace, idleTimeout);
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(connection);
        activeMQConnectionFactory.setUserName(username);
        activeMQConnectionFactory.setPassword(password);
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setMaximumRedeliveries(3);
        activeMQConnectionFactory.setRedeliveryPolicy(redeliveryPolicy);
        activeMQConnectionFactory.setClientID(clientId);
        return new CachingConnectionFactory(activeMQConnectionFactory);
    }

    // for if we need to write a message back to a specific queue
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate();
        jmsTemplate.setConnectionFactory(connectionFactory);
        jmsTemplate.setReceiveTimeout(5000); // Set the receive timeout to 5 seconds
        return jmsTemplate;
    }

    @Bean
    public JmsListenerContainerFactory<DefaultMessageListenerContainer> orchestratorEventQueueContainerFactory(
        ConnectionFactory orchestratorJmsConnectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setSessionAcknowledgeMode(2);
        factory.setConnectionFactory(orchestratorJmsConnectionFactory);
        factory.setReceiveTimeout(receiveTimeout);
        factory.setSessionTransacted(Boolean.TRUE);
        factory.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        factory.setMessageConverter(new CustomMessageConverter());
        return factory;
    }

    @Component
    public static class CustomMessageConverter implements MessageConverter {

        @Override
        public Message toMessage(Object object, Session session) throws JMSException, MessageConversionException {
            return session.createTextMessage(object.toString());
        }

        @Override
        public Object fromMessage(Message message) throws MessageConversionException {
            return message;
        }
    }
}
