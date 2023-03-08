FROM registry.access.redhat.com/ubi9/ubi-minimal:9.1.0-1760.1675784957

MAINTAINER Laurent Broudoux <laurent@microcks.io>

# Some version information
LABEL io.k8s.description="Microcks is Open Source Kubernetes native tool for API Mocking and Testing" \
      io.k8s.display-name="Microcks Application" \
      maintainer="Laurent Broudoux <laurent@microcks.io>"

# Install Java runtime
RUN microdnf install java-17-openjdk-headless openssl curl-minimal ca-certificates -y \
 && microdnf clean all \
 && mkdir -p /deployments

# JAVA_APP_DIR is used by run-java.sh for finding the binaries
ENV JAVA_APP_DIR=/deployments \
    JAVA_MAJOR_VERSION=8

# Agent bond including Jolokia and jmx_exporter
ADD ./src/main/docker/agent-bond-opts /opt/run-java-options
RUN mkdir -p /opt/agent-bond \
 && curl https://repo1.maven.org/maven2/io/fabric8/agent-bond-agent/1.2.0/agent-bond-agent-1.2.0.jar \
          -o /opt/agent-bond/agent-bond.jar \
 && chmod 444 /opt/agent-bond/agent-bond.jar \
 && chmod 755 /opt/run-java-options
ADD ./src/main/docker/jmx_exporter_config.yml /opt/agent-bond/
EXPOSE 8778 9779

# Set working directory at /deployments
WORKDIR /deployments
VOLUME /deployments/config

# Setup permissions for user '1001'. Necessary to permit running with a randomised UID
# Runtime user will need to be able to self-insert in /etc/passwd
# Also, use /dev/urandom to speed up startups
RUN chown 1001 /deployments \
    && chmod "g+rwX" /deployments \
    && chown 1001:root /deployments \
    && chmod g+rw /etc/passwd \
    && curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/1.3.8/run-java-sh-1.3.8-sh.sh \
            -o /deployments/run-java.sh \
    && mkdir -p /deployments/data \
    && chown 1001 /deployments/run-java.sh \
    && chmod 550 /deployments/run-java.sh \
    && echo "securerandom.source=file:/dev/urandom" >> /usr/lib/jvm/jre/lib/security/java.security

# Gives uid
USER 1001

# Copy corresponding jar file
COPY target/*.jar app.jar
EXPOSE 8080

# Run it
ENTRYPOINT [ "/deployments/run-java.sh" ]