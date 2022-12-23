package de.innovationhub.prox.keycloak.kafka;

import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaProducerFactory {

  private Producer<String, String> producer;
  private final KafkaConfigurationProperties configurationProperties;

  public KafkaProducerFactory(KafkaConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
  }

  private Producer<String, String> createProducer() {
    return createProducer(this.configurationProperties.getClientId(),
      this.configurationProperties.getBootstrapServers(),
      this.configurationProperties.getKafkaProducerProperties());
  }

  private Producer<String, String> createProducer(
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

  /**
   * Gets managed producer
   * @return producer instance
   * @throws ProducerNotAvailableException if no producer is available and cannot be built
   */
  public Producer<String, String> getProducer() {
    if(this.producer != null) {
      return this.producer;
    }

    try {
      return this.createProducer();
    } catch (KafkaException e) {
      throw new ProducerNotAvailableException();
    }
  }

  public void closeProducer() {
    this.producer.close();
    this.producer = null;
  }
}
