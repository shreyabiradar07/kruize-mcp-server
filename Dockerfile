FROM maven:3.9-eclipse-temurin-21 AS build
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml -B clean package

RUN ls -R /usr/src/app/target # For debugging

FROM eclipse-temurin:21-jre
WORKDIR /deployments

# Copy the executable JAR from the build stage
COPY --from=build /usr/src/app/target/kruize-mcp-server-1.0-SNAPSHOT-runner.jar /deployments/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]