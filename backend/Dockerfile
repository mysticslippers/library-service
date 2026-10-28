FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S library && adduser -S -G library library

WORKDIR /app
COPY --chown=library:library target/library-service-backend.jar app.jar

USER library
ENV TZ=UTC

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
