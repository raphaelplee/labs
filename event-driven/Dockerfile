FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY event-driven/pom.xml event-driven/pom.xml
RUN mvn -f event-driven/pom.xml dependency:go-offline -q
COPY event-driven/src event-driven/src
RUN mvn -f event-driven/pom.xml package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=build /app/event-driven/target/transflow-core-*.jar app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
