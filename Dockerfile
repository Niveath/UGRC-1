FROM ubuntu:latest

FROM openjdk:8-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Install Maven to build the project
RUN apt-get update && apt-get install -y maven

COPY src ./src

# Copy the SOOT jar with dependencies
COPY libs/soot-4.4.1-jar-with-dependencies.jar ./libs/soot-4.4.1-jar-with-dependencies.jar

# Build the application
RUN mvn package -DskipTests

# Define the entry point for the container
CMD ["java", "-cp", "target/my-java-app.jar:libs/soot-4.4.1-jar-with-dependencies.jar", "Driver"]
