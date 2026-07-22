# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:26-jre AS runtime

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
