package com.athenet.competition.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RabbitConfigTest {
    public static class FailingConsumer {
        int calls;
        public void consume(Object channel, Message message) {
            calls++;
            throw new IllegalStateException("failure for retry test");
        }
    }

    @Test void failuresAreRetriedThenRejectedWithoutRequeue() {
        FailingConsumer target = new FailingConsumer();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new RabbitConfig().retryInterceptor());
        FailingConsumer proxy = (FailingConsumer) factory.getProxy();
        var error = assertThrows(org.springframework.amqp.rabbit.support.ListenerExecutionFailedException.class,
                () -> proxy.consume(null, new Message(new byte[0], new MessageProperties())));
        assertInstanceOf(AmqpRejectAndDontRequeueException.class, error.getCause());
        assertEquals(4, target.calls);
    }

    @Test void deadLetterBindingPreservesOriginalEventRoutingKey() {
        RabbitConfig config = new RabbitConfig();
        assertEquals("athenet.dlx", config.eventsQueue().getArguments().get("x-dead-letter-exchange"));
        assertFalse(config.eventsQueue().getArguments().containsKey("x-dead-letter-routing-key"));
        assertEquals("evento.*", config.eventsDlqBinding(config.eventsDlq(), config.deadLetterExchange()).getRoutingKey());
    }
}
