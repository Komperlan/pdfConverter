# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
RUN mvn -pl backend -am -Dmaven.test.skip=true dependency:go-offline

COPY backend/src backend/src
RUN mvn -pl backend -am -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 docconverter \
    && useradd --system --uid 10001 --gid docconverter --home-dir /app docconverter

WORKDIR /app

COPY --from=build --chown=docconverter:docconverter \
    /workspace/backend/target/docconverter-backend-0.1.0-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/storage \
    && chown docconverter:docconverter /app/storage

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
