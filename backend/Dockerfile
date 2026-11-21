FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER 10001
CMD ["java", "-jar", "app.jar"]
