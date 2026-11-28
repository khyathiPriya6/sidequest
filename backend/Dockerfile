# Local/staging build; do not embed configuration or secrets into images.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system sidequest && useradd --system --gid sidequest sidequest
COPY --from=build --chown=sidequest:sidequest /workspace/target/sidequest-api-0.1.0-SNAPSHOT.jar /app/sidequest.jar
USER sidequest
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/sidequest.jar"]
