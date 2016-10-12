FROM java:8
VOLUME /tmp
ADD *.jar app.jar
RUN sh -c 'touch /app.jar'
ENTRYPOINT ["java","-Dspring.profiles.active=prod -Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
