FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Resolve dependencies before copying sources for better layer caching.
RUN ./gradlew --no-daemon dependencies > /tmp/gradle-deps.txt || true

COPY src src
COPY db db
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN addgroup --system appgroup \
    && adduser --system --ingroup appgroup appuser

COPY --from=build /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup /app/app.jar

EXPOSE 8080
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
