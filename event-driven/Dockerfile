FROM eclipse-temurin:25-jre-jammy AS extractor
WORKDIR /builder
COPY event-driven/target/transflow-core-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-jammy
WORKDIR /application
COPY --from=extractor /builder/extracted/dependencies/ ./
COPY --from=extractor /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor /builder/extracted/application/ ./
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar application.jar"]
