FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/ src/
RUN ./gradlew --no-daemon --console=plain test bootJar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
COPY render-egress-check.sh /app/render-egress-check.sh

ENV SERVER_PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["/bin/sh", "/app/render-egress-check.sh"]
