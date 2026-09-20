FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY settings.xml .
RUN mvn -q -s settings.xml -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -s settings.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
# docker-cli is used only to inspect the pre-created lab containers. No scenario gets this tool/socket.
RUN apk add --no-cache docker-cli && mkdir -p /app/data /app/scenarios
WORKDIR /app
COPY --from=build /build/target/control-plane-1.0.0.jar app.jar
# Root is required only because the Docker Engine socket's group ID is host-specific.
# This container is the privileged control plane; scenario containers remain unprivileged.
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
