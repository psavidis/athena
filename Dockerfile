# Builds the real, deployable artifact so integration tests can treat it as
# an actual black box (CODE_STYLE.md F.6) — no whitebox @SpringBootTest.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY athena-core/pom.xml athena-core/pom.xml
COPY athena-plugin-java/pom.xml athena-plugin-java/pom.xml
COPY athena-plugin-spring/pom.xml athena-plugin-spring/pom.xml
COPY athena-plugin-maven/pom.xml athena-plugin-maven/pom.xml
COPY athena-app/pom.xml athena-app/pom.xml
COPY athena-core/src ./athena-core/src
COPY athena-plugin-java/src ./athena-plugin-java/src
COPY athena-plugin-spring/src ./athena-plugin-spring/src
COPY athena-plugin-maven/src ./athena-plugin-maven/src
COPY athena-app/src ./athena-app/src
RUN mvn -q -DskipTests package -pl athena-app -am

FROM eclipse-temurin:21-jre
COPY --from=build /app/athena-app/target/athena-app-*.jar /app/app.jar
EXPOSE 7332
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
