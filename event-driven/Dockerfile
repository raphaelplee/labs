FROM eclipse-temurin:25-jre-jammy
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /application
COPY event-driven/target/transflow-core-*.jar application.jar
USER app
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar application.jar"]
