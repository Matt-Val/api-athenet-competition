package com.athenet.competition.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/*
    Declara el topic de marcador en vivo para que KafkaAdmin lo cree al arrancar
    (el broker tiene auto.create.topics.enable=false).
*/
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic marcadorTopic(
            @Value("${app.kafka.topic.marcador:marcador}") String topic,
            @Value("${app.kafka.topic.marcador-replicas:1}") int replicas) {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(replicas)
                .build();
    }
}
