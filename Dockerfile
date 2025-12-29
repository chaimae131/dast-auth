# ==== Stage 1: Build the application ====
FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml from user-service directory
COPY user-service/.mvn/ .mvn
COPY user-service/mvnw user-service/pom.xml ./
RUN chmod +x mvnw

# Download dependencies (leverages Docker layer caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build the application
COPY user-service/src ./src
RUN ./mvnw clean package -DskipTests

# ==== Stage 2: Run the application ====
# Use a lean JRE image for the final runtime
FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
WORKDIR /app

# Copy the built JAR file from the 'build' stage to the 'runtime' stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the app runs on (default is 8080)
EXPOSE 8080

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
