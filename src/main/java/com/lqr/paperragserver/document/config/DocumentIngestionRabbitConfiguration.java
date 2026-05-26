package com.lqr.paperragserver.document.config;

import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 文档异步入库 RabbitMQ 拓扑配置。
 */
@Configuration
public class DocumentIngestionRabbitConfiguration {

    public static final String EXCHANGE = "paper.document.ingestion.exchange";
    public static final String QUEUE = "paper.document.ingestion.queue";
    public static final String ROUTING_KEY = "paper.document.ingestion";
    public static final String DLQ = "paper.document.ingestion.dlq";
    public static final String DLX = "paper.document.ingestion.dlx";

    @Bean
    public DirectExchange documentIngestionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange documentIngestionDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue documentIngestionQueue() {
        return new Queue(QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX,
                "x-dead-letter-routing-key", DLQ
        ));
    }

    @Bean
    public Queue documentIngestionDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    public Binding documentIngestionBinding(Queue documentIngestionQueue, DirectExchange documentIngestionExchange) {
        return BindingBuilder.bind(documentIngestionQueue).to(documentIngestionExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding documentIngestionDeadLetterBinding(Queue documentIngestionDeadLetterQueue,
                                                      DirectExchange documentIngestionDeadLetterExchange) {
        return BindingBuilder.bind(documentIngestionDeadLetterQueue).to(documentIngestionDeadLetterExchange).with(DLQ);
    }

    @Bean
    public MessageConverter documentIngestionMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory documentIngestionListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter documentIngestionMessageConverter,
            DocumentIngestionProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(documentIngestionMessageConverter);
        factory.setConcurrentConsumers(properties.listener().concurrency());
        factory.setMaxConcurrentConsumers(properties.listener().maxConcurrency());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}