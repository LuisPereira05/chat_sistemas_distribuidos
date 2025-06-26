# Use Maven image to build the app
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /app

# Copy Maven files and source code
COPY pom.xml .
COPY src ./src

# Build the project (package as jar)
RUN mvn clean package

# Use a smaller JRE image to run the app
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/chat-app-a-1.0-SNAPSHOT.jar app.jar

COPY wait-for-it.sh .

RUN chmod +x wait-for-it.sh

CMD ["./wait-for-it.sh", "rabbitmq:5672", "--timeout=30", "--", "java", "-jar", "app.jar"]