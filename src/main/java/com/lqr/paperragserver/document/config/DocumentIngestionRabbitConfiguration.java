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

    /**
     * 创建文档入库直连交换机。
     *
     * @return 文档入库交换机实例
     */
    @Bean
    public DirectExchange documentIngestionExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * 创建文档入库死信交换机。
     *
     * @return 死信交换机实例
     */
    @Bean
    public DirectExchange documentIngestionDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /**
     * 创建文档入库队列，绑定死信交换机以便消费失败时自动转入死信队列。
     *
     * @return 文档入库队列实例
     */
    @Bean
    public Queue documentIngestionQueue() {
        return new Queue(QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX,
                "x-dead-letter-routing-key", DLQ
        ));
    }

    /**
     * 创建文档入库死信队列，用于接收消费失败的消息。
     *
     * @return 死信队列实例
     */
    @Bean
    public Queue documentIngestionDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    /**
     * 将文档入库队列绑定到入库交换机。
     *
     * @param documentIngestionQueue 文档入库队列
     * @param documentIngestionExchange 文档入库交换机
     * @return 绑定关系
     */
    @Bean
    public Binding documentIngestionBinding(Queue documentIngestionQueue, DirectExchange documentIngestionExchange) {
        return BindingBuilder.bind(documentIngestionQueue).to(documentIngestionExchange).with(ROUTING_KEY);
    }

    /**
     * 将死信队列绑定到死信交换机。
     *
     * @param documentIngestionDeadLetterQueue 死信队列
     * @param documentIngestionDeadLetterExchange 死信交换机
     * @return 绑定关系
     */
    @Bean
    public Binding documentIngestionDeadLetterBinding(Queue documentIngestionDeadLetterQueue,
                                                      DirectExchange documentIngestionDeadLetterExchange) {
        return BindingBuilder.bind(documentIngestionDeadLetterQueue).to(documentIngestionDeadLetterExchange).with(DLQ);
    }

    /**
     * 创建文档入库专用的 JSON 消息转换器。
     *
     * @return Jackson JSON 消息转换器
     */
    @Bean
    public MessageConverter documentIngestionMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 创建文档入库监听器容器工厂，配置并发消费者数量和消息转换器。
     *
     * @param connectionFactory RabbitMQ 连接工厂
     * @param documentIngestionMessageConverter 文档入库消息转换器
     * @param properties 文档入库配置属性
     * @return 配置好的监听器容器工厂
     */
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