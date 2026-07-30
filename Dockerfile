# ---- Build stage ----
# Red Hat UBI9 OpenJDK 21 image includes Maven and runs as non-root (uid 185)
FROM registry.access.redhat.com/ubi9/openjdk-21:1.24-2.1782292637 AS build

USER root
COPY pom.xml /usr/src/app/pom.xml
COPY src /usr/src/app/src
RUN mvn -f /usr/src/app/pom.xml -B clean package

# ---- Runtime stage ----
# Minimal Red Hat UBI9 JRE image; runs as non-root user 185 by default
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24-2.1782293370

WORKDIR /deployments

# Copy the executable JAR from the build stage
COPY --from=build /usr/src/app/target/*-runner.jar /deployments/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
