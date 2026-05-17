FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY event-driven/target/transflow-core-*.jar app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
