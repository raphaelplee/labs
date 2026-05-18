FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY event-driven/pom.xml event-driven/pom.xml
RUN mvn -f event-driven/pom.xml dependency:go-offline -q
COPY event-driven/src event-driven/src
RUN mvn -f event-driven/pom.xml package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-jammy AS extractor
WORKDIR /builder
COPY --from=build /app/event-driven/target/transflow-core-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-jammy
WORKDIR /application
COPY --from=extractor /builder/extracted/dependencies/ ./
COPY --from=extractor /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor /builder/extracted/application/ ./
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar application.jar"]
