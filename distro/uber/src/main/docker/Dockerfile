FROM registry.access.redhat.com/ubi9/ubi-minimal:9.7-1763362218

# Some version information
LABEL maintainer="Laurent Broudoux <laurent@microcks.io>" \
      org.opencontainers.image.authors="Laurent Broudoux <laurent@microcks.io>" \
      org.opencontainers.image.title="Microcks Application Uber" \
      org.opencontainers.image.description="Microcks is Open Source cloud-native native tool for API Mocking and Testing" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.documentation="https://github.com/microcks/microcks/tree/master/distro/uber" \
      io.artifacthub.package.readme-url="https://raw.githubusercontent.com/microcks/microcks/master/distro/uber/README.md"

# Install Java runtime
RUN microdnf install java-21-openjdk-headless openssl curl-minimal ca-certificates -y \
 && microdnf clean all \
 && rm /var/lib/rpm/rpmdb.sqlite \
 && mkdir -p /deployments

# JAVA_APP_DIR is used by run-java.sh for finding the binaries
ENV JAVA_APP_DIR=/deployments \
    JAVA_MAJOR_VERSION=11

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
COPY target/*-exec.jar app.jar
EXPOSE 8080

# Run it in uber mode with AOT support
ENV SPRING_PROFILES_ACTIVE=uber
ENV JAVA_OPTIONS="-Dspring.aot.enabled=true"
ENTRYPOINT [ "/deployments/run-java.sh" ]