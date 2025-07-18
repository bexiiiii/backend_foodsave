# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests


# ---------- Run stage ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Expose the default Spring Boot port
EXPOSE 8080

# Set JVM options (can be overridden at runtime)
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# Use shell form to allow environment variable expansion
ENTRYPOINT sh -c "java $JAVA_OPTS -jar app.jar"
