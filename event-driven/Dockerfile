FROM eclipse-temurin:25-jre-jammy
WORKDIR /application
COPY event-driven/target/transflow-core-*.jar application.jar
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar application.jar"]
