package com.athenet.competition.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

/* 
    Exchange, colas y bindings como beans de Spring. Mismo patrón que mostró
    el profesor en el repo cloud_rabbit (RabbitConfig con @Bean por Exchange/Queue/Binding,
    Jackson2JsonMessageConverter con trusted packages, listener con ack manual) - 
    adaptado a lo que si pide Athenet: un solo TropicExchange compartido entre los microservicios,
    Con reitentos antes del DLQ.
*/
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "athenet.topic";

    /* 
        Ojito: A diferencia de la demo del profesor (donde el DLX es un 
        FanoutExchange porque solo hay una DLQ), en Athenet van a convivir varias DLQs
        (una por microservicio) colgando del mismo "athenet.dlx". Si fuera Fanout,
        cada mensaje muerto se copiaría a TODAS las DLQs. Por eso es Tropic, y cada DLQ
        se bindea con el mismo patrón de routing key que su cola principal.
    */
    public static final String DEAD_LETTER_EXCHANGE = "athenet.dlx";

    public static final String EVENTS_QUEUE = "competencia.eventos";
    public static final String EVENTS_DLQ = "competencia.eventos.dlq";
    public static final String EVENTS_ROUTING_PATTERN = "evento.*";

    public static final String RESULT_REGISTERED_ROUTING_KEY = "resultado.registrado";

    /* 
        ObjectMapper Jackson 2.x compartido: lo usa el converter de abajo 
        (para publicar) y EventMessageListener (para deserializar a mano al
        consumir) - mismo mapper en los dos lados, nada de depender de 
        autoconfig entre Jackson 2 y Jackson 3.
    */
    @Bean
    ObjectMapper rabbitObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /* 
        Se usa solo para PUBLICAR (RabbitTemplate). Para consumir, probamos
        dejar que Spring convierta automáticamente al tipo del @RabbitListener
        (incluso forzando TypePrecedence.INFERRED) y falló de forma consistente
        con "Cannot convert From LinkedHashMap" - Algo en cómo MessaginMessageListenerAdapter
        arma el argumento en esta combinación de versiones no está usando este converter como 
        se espera. En vez de seguir pleneando con la conversión automática, EventMessageListener
        deserializa el body a mano con RabbitObjectMapper.
    */
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

    /* 
        3 reintentos con backoff exponencial (1s, 2s, 4s) antes de dejar que el mensaje
        caiga a la DLQ. RefjectAndDontRequeueRecover es lo que, agotados los reintentos, 
        hace el nack final sin volver a encolar - ahí entra el x-dead-letter-exchange de la cola.
    */
    @Bean
    StatelessRetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 2.0, 8000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /* 
        Auto: el interceptor de reintentos necesita que el contenedor maneje el ack/nack 
        de manera automática según el resultado de la llamada (incluidos los reintentos).
        ----
        Si fuera manual, el propio listener, entonces, queda como lógica de negocio
        simple: si tira una excepción, el interceptor reintenta, agotados los 3 reintentos
        después del intento inicial, RejectAndDontRequeueRecoverer hace el rechazo final
        sin reencolar.
    */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            StatelessRetryOperationsInterceptor retryInterceptor,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor);
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    TopicExchange athenetTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    // Cola que consume evento.creado/actualizado/cancelado
    @Bean
    Queue eventsQueue() {
        /* 
            Sin "x-dead-letter-routing-key" al no setearlo, RabbitMQ conserva
            la routing key original del mensaje (evento.creado/actualizado/cancelado)
            al republicarlo en el DLX - que es justo lo que necesita el binding de abajo para
            reconocerlo.
        */
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        return new Queue(EVENTS_QUEUE, true, false, false, args);
    }

    @Bean
    Queue eventsDlq() {
        return new Queue(EVENTS_DLQ, true);
    }

    @Bean
    Binding eventsBinding(Queue eventsQueue, TopicExchange athenetTopicExchange) {
        return BindingBuilder.bind(eventsQueue).to(athenetTopicExchange).with(EVENTS_ROUTING_PATTERN);
    }

    @Bean
    Binding eventsDlqBinding(Queue eventsDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(eventsDlq).to(deadLetterExchange).with(EVENTS_ROUTING_PATTERN);
    }
}
