FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY service/build.gradle.kts service/build.gradle.kts
COPY service/config service/config
COPY service/src service/src

RUN chmod +x gradlew && ./gradlew :service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
