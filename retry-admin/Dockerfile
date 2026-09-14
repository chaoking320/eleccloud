FROM eclipse-temurin:17-jre
VOLUME /tmp
ARG JAR_FILE=target/retry-admin-*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
