package com.aiview.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SCORING_EXCHANGE = "aiview.exchange";
    public static final String SCORING_QUEUE = "aiview.interview.scoring";
    public static final String SCORING_ROUTING_KEY = "interview.scoring";

    @Bean
    public DirectExchange scoringExchange() {
        return new DirectExchange(SCORING_EXCHANGE, true, false);
    }

    @Bean
    public Queue scoringQueue() {
        return new Queue(SCORING_QUEUE, true);
    }

    @Bean
    public Binding scoringBinding() {
        return BindingBuilder.bind(scoringQueue()).to(scoringExchange()).with(SCORING_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}