FROM eclipse-temurin:8-jre
VOLUME /tmp
ARG JAR_FILE=target/retry-server-*-exec.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
