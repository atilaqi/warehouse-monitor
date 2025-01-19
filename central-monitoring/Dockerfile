# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Copy Maven configuration first (for dependency caching)
COPY pom.xml /build/pom.xml
COPY central-monitoring/pom.xml /build/central-monitoring/pom.xml

# Download dependencies only (caching optimization)
RUN mvn dependency:go-offline -f /build/central-monitoring/pom.xml

# Copy source code
COPY central-monitoring/src /build/central-monitoring/src

# Build the application
RUN mvn clean package -f /build/central-monitoring/pom.xml -DskipTests

# Run Stage (Smaller Image)
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Use a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built artifact
COPY --from=build /build/central-monitoring/target/*.jar app.jar

# Expose the application port (if applicable)
#EXPOSE 8080  # Change this based on your application's configuration

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
