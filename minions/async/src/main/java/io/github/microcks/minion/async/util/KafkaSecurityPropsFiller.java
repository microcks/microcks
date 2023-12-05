package io.github.microcks.minion.async.util;

import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.util.Properties;

public class KafkaSecurityPropsFiller {

   /** Get a JBoss logging logger. */
   private static final Logger logger = Logger.getLogger(KafkaSecurityPropsFiller.class);

   public static final String SECURITY_PROTOCOL_PROP_NAME = "security.protocol";

   private KafkaSecurityPropsFiller() {
      // Hide default no argument constructor as it's a utility class.
   }

   public static void fillSpecificSecurityProps(String securityProtocolValue, Properties props, Config config) {
      switch (securityProtocolValue) {
         case "SASL_SSL" -> {
            logger.debug("Adding SASL_SSL specific connection properties");
            props.put(SECURITY_PROTOCOL_PROP_NAME, SecurityProtocol.SASL_SSL.name);
            props.put(SaslConfigs.SASL_MECHANISM, config.getValue("kafka.sasl.mechanism", String.class));
            props.put(SaslConfigs.SASL_JAAS_CONFIG, config.getValue("kafka.sasl.jaas.config", String.class));
            if (config.getOptionalValue("kafka.sasl.login.callback.handler.class", String.class).isPresent()) {
               props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, config.getValue("kafka.sasl.login.callback.handler.class", String.class));
            }
         }
         case "SSL" -> {
            logger.debug("Adding SSL specific connection properties");
            props.put(SECURITY_PROTOCOL_PROP_NAME, SecurityProtocol.SSL.name);
            props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, config.getValue("kafka.ssl.keystore.location", String.class));
            props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, config.getValue("kafka.ssl.keystore.password", String.class));
            props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, config.getValue("kafka.ssl.keystore.type", String.class));
         }
         default -> {
            // No action is required for other protocols for now
         }
      }
   }
}
