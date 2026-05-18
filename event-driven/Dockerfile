FROM eclipse-temurin:25-jdk-jammy AS build
ARG MAVEN_VERSION=3.9.9
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
       | tar xz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn \
    && apt-get remove -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*
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
