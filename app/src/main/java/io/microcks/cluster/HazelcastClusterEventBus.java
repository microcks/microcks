/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.cluster;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TopicConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A {@code ClusterEventBus} based on Hazelcast {@code ITopic}. Topics have global ordering enabled so that all the
 * members see the same sequence of messages. How members find each other depends on the
 * {@code microcks.cluster.discovery} property:
 * <ul>
 * <li>{@code standalone} (default): no discovery, a single member that does not try to join anybody,</li>
 * <li>{@code kubernetes}: members are looked up through the Kubernetes API (service name) or a headless service DNS
 * name,</li>
 * <li>{@code tcp-ip}: members are listed in {@code microcks.cluster.members} (compose or VM deployments).</li>
 * </ul>
 * This bean is not part of the uber distribution, where there is only one instance and Hazelcast is not packaged.
 * @author laurent
 */
@ApplicationScoped
@IfBuildProperty(name = "microcks.cluster.enabled", stringValue = "true", enableIfMissing = true)
public class HazelcastClusterEventBus implements ClusterEventBus {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(HazelcastClusterEventBus.class);

   private final HazelcastInstance hazelcast;

   /**
    * Build a new bus and start the local Hazelcast member.
    * @param discovery      The discovery mode: standalone, kubernetes or tcp-ip
    * @param clusterName    The name of the cluster, members only join members of the same cluster
    * @param port           The Hazelcast member port
    * @param members        Comma separated members addresses for the tcp-ip mode
    * @param k8sNamespace   The Kubernetes namespace for the kubernetes mode
    * @param k8sServiceName The Kubernetes service name for the kubernetes mode
    * @param k8sServiceDns  The headless service DNS name for the kubernetes mode, no RBAC needed when set
    */
   public HazelcastClusterEventBus(
         @ConfigProperty(name = "microcks.cluster.discovery", defaultValue = "standalone") String discovery,
         @ConfigProperty(name = "microcks.cluster.name", defaultValue = "microcks") String clusterName,
         @ConfigProperty(name = "microcks.cluster.port", defaultValue = "5701") int port,
         @ConfigProperty(name = "microcks.cluster.members") Optional<String> members,
         @ConfigProperty(name = "microcks.cluster.kubernetes.namespace") Optional<String> k8sNamespace,
         @ConfigProperty(name = "microcks.cluster.kubernetes.service-name", defaultValue = "microcks") String k8sServiceName,
         @ConfigProperty(name = "microcks.cluster.kubernetes.service-dns") Optional<String> k8sServiceDns) {
      Config config = buildConfig(discovery, clusterName, port, members.orElse(""), k8sNamespace.orElse(null),
            k8sServiceName, k8sServiceDns.orElse(null));
      this.hazelcast = Hazelcast.newHazelcastInstance(config);
      log.info("Hazelcast cluster member started, discovery mode is {}", discovery);
   }

   /**
    * Build the Hazelcast configuration for a discovery mode.
    * @param discovery      The discovery mode: standalone, kubernetes or tcp-ip
    * @param clusterName    The name of the cluster
    * @param port           The member port
    * @param members        Comma separated members addresses for the tcp-ip mode
    * @param k8sNamespace   The Kubernetes namespace (may be null to use the pod one)
    * @param k8sServiceName The Kubernetes service name
    * @param k8sServiceDns  The headless service DNS name (may be null)
    * @return The Hazelcast configuration
    */
   static Config buildConfig(String discovery, String clusterName, int port, String members, String k8sNamespace,
         String k8sServiceName, String k8sServiceDns) {
      Config config = new Config();
      config.setClusterName(clusterName);
      config.setProperty("hazelcast.logging.type", "slf4j");
      config.setProperty("hazelcast.phone.home.enabled", "false");
      config.setProperty("hazelcast.shutdownhook.enabled", "false");
      // Ordering is global per topic: all members receive messages of a topic in the same order.
      config.addTopicConfig(new TopicConfig("microcks-*").setGlobalOrderingEnabled(true));

      NetworkConfig network = config.getNetworkConfig();
      network.setPort(port).setPortAutoIncrement(true);
      JoinConfig join = network.getJoin();
      join.getAutoDetectionConfig().setEnabled(false);
      join.getMulticastConfig().setEnabled(false);
      join.getTcpIpConfig().setEnabled(false);
      join.getKubernetesConfig().setEnabled(false);

      switch (discovery) {
         case "standalone" -> log.debug("Hazelcast discovery disabled, running as a single member");
         case "tcp-ip" -> {
            join.getTcpIpConfig().setEnabled(true);
            Arrays.stream(members.split(",")).map(String::trim).filter(m -> !m.isEmpty())
                  .forEach(m -> join.getTcpIpConfig().addMember(m));
         }
         case "kubernetes" -> {
            join.getKubernetesConfig().setEnabled(true);
            if (k8sServiceDns != null && !k8sServiceDns.isBlank()) {
               join.getKubernetesConfig().setProperty("service-dns", k8sServiceDns);
            } else {
               join.getKubernetesConfig().setProperty("service-name", k8sServiceName);
               if (k8sNamespace != null && !k8sNamespace.isBlank()) {
                  join.getKubernetesConfig().setProperty("namespace", k8sNamespace);
               }
            }
         }
         default -> throw new IllegalArgumentException(
               "Unknown microcks.cluster.discovery '" + discovery + "', expecting standalone, kubernetes or tcp-ip");
      }
      return config;
   }

   @Override
   public void publish(String topic, String payload) {
      hazelcast.<String>getTopic(topic).publish(payload);
   }

   @Override
   public void subscribe(String topic, Consumer<String> listener) {
      hazelcast.<String>getTopic(topic).addMessageListener(message -> listener.accept(message.getMessageObject()));
   }

   @PreDestroy
   void shutdown() {
      hazelcast.shutdown();
   }
}
