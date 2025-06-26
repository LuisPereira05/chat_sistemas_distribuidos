# Use Maven image to build the app
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /app

# Copy Maven files and source code
COPY pom.xml .
COPY src ./src

# Build the project (package as jar)
RUN mvn clean package -DskipTests

# Use a smaller JRE image to run the app
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Set environment variables, e.g. CLIENT_ID if needed
ENV CLIENT_ID=clientA

# Run the Java app
CMD ["java", "-jar", "app.jar"]