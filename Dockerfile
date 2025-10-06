FROM gradle:8.5-jdk17 AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/soshopay-mock-api.jar ./app.jar

# Create directories for data persistence
RUN mkdir -p /app/data /app/uploads

EXPOSE 8080

ENV PORT=8080

CMD ["java", "-jar", "app.jar"]