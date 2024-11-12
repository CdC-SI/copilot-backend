FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . /app
RUN mvn clean package -DskipTests=true

FROM eclipse-temurin:21
WORKDIR /app

COPY --from=build /app/target/copilot-backend*.jar /app/copilot-backend.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "copilot-backend.jar"]