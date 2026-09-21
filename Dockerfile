FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/plataforma-sso-0.1.0.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65"
USER 10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=cloud"]
