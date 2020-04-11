FROM fabric8/java-ubi-openjdk8-jdk:latest

MAINTAINER Laurent Broudoux <laurent.broudoux@gmail.com>

# Some version information
LABEL io.k8s.description="Microcks is Open Source Kubernetes native tool for API Mocking and Testing" \
      io.k8s.display-name="Microcks Application" \
      maintainer="Laurent Broudoux <laurent@microcks.io>"

# Prepare a volume for external configuration.
VOLUME /deployments/config

# Copy files and install dependencies
ADD *.jar /deployments/app.jar