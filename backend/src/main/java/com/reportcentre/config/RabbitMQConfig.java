package com.reportcentre.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue}")
    private String queue;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue watermarkQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding binding(Queue watermarkQueue, DirectExchange reportExchange) {
        return BindingBuilder.bind(watermarkQueue).to(reportExchange).with(routingKey);
    }
}
