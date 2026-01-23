# ==========================================
# Stage 1: The Builder (for code compilation)
# ==========================================
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.10_1.9.8_3.4.0 AS builder

WORKDIR /app

# 1. Cache Dependencies in 'project' dir
COPY build.sbt .
COPY project/ project/
RUN sbt update

# 2. Build the Application
# copy source code
COPY . .
# Use 'assembly' to create a "Fat JAR" with all dependencies included
RUN sbt assembly

# ==========================================
# Stage 2: The Runner (for running the code)
# ==========================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the Fat JAR from the builder stage
COPY --from=builder /app/target/scala-*/app.jar app.jar

# Set jar ownership
RUN chown appuser:appuser app.jar

# Switch to non-root user
USER appuser

# Expose the port your app runs on (e.g., 8080 or 9000)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]