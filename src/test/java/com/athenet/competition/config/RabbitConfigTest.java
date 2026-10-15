package com.athenet.competition.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;

class RabbitConfigTest {

    @Test void publishesToDurableSharedTopicExchange() {
        TopicExchange exchange = new RabbitConfig().athenetTopicExchange();
        assertEquals("athenet.topic", exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }
}
