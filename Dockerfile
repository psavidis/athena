# Builds the real, deployable artifact so integration tests can treat it as
# an actual black box (CODE_STYLE.md F.6) — no whitebox @SpringBootTest.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre
COPY --from=build /app/target/athena-*.jar /app/app.jar
EXPOSE 7332
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
