FROM registry.access.redhat.com/ubi9/ubi-minimal:9.8-1785906621

# Some version information
LABEL maintainer="Laurent Broudoux <laurent@microcks.io>" \
      org.opencontainers.image.authors="Laurent Broudoux <laurent@microcks.io>" \
      org.opencontainers.image.title="Microcks Application Uber" \
      org.opencontainers.image.description="Microcks is Open Source cloud-native native tool for API Mocking and Testing" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.documentation="https://github.com/microcks/microcks/tree/master/distro/uber" \
      io.artifacthub.package.readme-url="https://raw.githubusercontent.com/microcks/microcks/master/distro/uber/README.md"

# Install Java runtime
RUN microdnf install java-25-openjdk-headless openssl curl-minimal ca-certificates -y \
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

# Extract the Spring Boot fat jar for a faster classloading layout, then perform a training run
# (exiting once the Spring context refresh completes) to produce a JVM AOT cache (JEP 514).
# This dramatically reduces classloading/linking work at startup, which is the dominant boot cost
# on CPU-constrained containers. Lazy initialization is disabled during training to maximize the
# number of classes recorded in the cache.
RUN java -Djarmode=tools -jar app.jar extract --destination application \
 && rm app.jar \
 && mkdir -p /deployments/lib \
 && SPRING_MAIN_LAZY_INITIALIZATION=false SPRING_PROFILES_ACTIVE=uber \
      java -XX:+UseG1GC -XX:AOTCacheOutput=/deployments/app.aot -Dspring.context.exit=onRefresh -jar application/app.jar

# Run it in uber mode. The JVM AOT cache replaces Spring AOT (-Dspring.aot.enabled=true): it
# provides equivalent-or-better startup gains while keeping bean conditions evaluated at runtime.
# The extracted layout is launched via classpath + main class (the Spring Boot loader is no longer
# used). /deployments/lib/* remains on the classpath so that custom extension libraries can simply
# be mounted or copied there - no PropertiesLauncher/loader.path setting is needed anymore.
# JDK_JAVA_OPTIONS is additive and picked up by the java launcher, so the AOT cache stays active
# even when deployments override JAVA_OPTIONS. MaxRAMPercentage=70 replaces the too-conservative
# 25% JVM default. G1 is pinned to match the GC used during the training run: the Red Hat build
# of JDK 25 cannot map the archived heap objects of the cache under the SerialGC that ergonomics
# select on small containers (1 CPU / <2GB). To use another GC, override JDK_JAVA_OPTIONS itself
# (dropping the -XX:AOTCache option) instead of adding the GC flag to JAVA_OPTIONS.
ENV SPRING_PROFILES_ACTIVE=uber \
    JAVA_MAIN_CLASS=io.github.microcks.MicrocksApplication \
    JAVA_CLASSPATH="/deployments/application/app.jar:/deployments/lib/*" \
    JDK_JAVA_OPTIONS="-XX:AOTCache=/deployments/app.aot -XX:MaxRAMPercentage=70.0 -XX:+UseG1GC"

ENTRYPOINT [ "/deployments/run-java.sh" ]