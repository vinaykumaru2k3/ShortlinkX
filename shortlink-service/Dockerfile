# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY shortlink-service/pom.xml shortlink-service/
COPY api-gateway/pom.xml api-gateway/
COPY common/src common/src
COPY shortlink-service/src shortlink-service/src
RUN mvn clean package -pl shortlink-service -am -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/shortlink-service/target/shortlink-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
