package com.athenet.competition.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/*
    Exchange y publicación como beans de Spring. Mismo patrón que mostró
    el profesor en el repo cloud_rabbit (RabbitConfig con @Bean por Exchange,
    Jackson2JsonMessageConverter con trusted packages) - adaptado a lo que si pide
    Athenet: un solo TopicExchange compartido entre los microservicios.
    Competencia solo publica: cada consumidor (Notificaciones) declara su propia
    cola, binding y DLQ.
*/
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "athenet.topic";

    public static final String RESULT_REGISTERED_ROUTING_KEY = "resultado.registrado";

    /*
        ObjectMapper Jackson 2.x para el converter de abajo: nada de depender de
        autoconfig entre Jackson 2 y Jackson 3.
    */
    @Bean
    ObjectMapper rabbitObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper rabbitObjectMapper) {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("com.athenet.competition.messaging");
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(rabbitObjectMapper);
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    RabbitTemplate outboxRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setUsePublisherConnection(true);
        return template;
    }

    @Bean
    TopicExchange athenetTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
