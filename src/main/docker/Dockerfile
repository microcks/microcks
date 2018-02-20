FROM openjdk:8-jre-alpine

MAINTAINER Laurent Broudoux <laurent.broudoux@gmail.com>

VOLUME /config

# Copy files and install dependencies
ADD *.jar app.jar

ENV JAVA_OPTS=""

ENTRYPOINT exec java $JAVA_OPTS -Dspring.profiles.active=prod -Djava.security.egd=file:/dev/./urandom -jar /app.jar
