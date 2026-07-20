package me.ifmo.backend.shared.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableKafkaRetryTopic
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaMessagingConfig {

    @Bean
    public NewTopic circulationEventsTopic(KafkaMessagingProperties properties) {
        return TopicBuilder.name(properties.topics().circulation())
                .partitions(properties.topic().partitions())
                .replicas(properties.topic().replicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(properties.topic().retentionMs()))
                .build();
    }
}
