package de.innovationhub.prox.keycloak.kafka;

import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaProducerFactory {

  public Producer<String, String> createProducer(
    KafkaConfigurationProperties configurationProperties
  ) {
    return createProducer(configurationProperties.getClientId(),
      configurationProperties.getBootstrapServers(),
      configurationProperties.getKafkaProducerProperties());
  }

  public Producer<String, String> createProducer(
    String clientId,
    String bootstrapServer,
    Map<String, Object> optionalProperties
  ) {
    Properties props = new Properties();

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
    props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");

    optionalProperties.forEach(props::put);

    // fix Class org.apache.kafka.common.serialization.StringSerializer could not be
    // found. see https://stackoverflow.com/a/50981469
    Thread.currentThread().setContextClassLoader(null);

    return new KafkaProducer<>(props);
  }
}
