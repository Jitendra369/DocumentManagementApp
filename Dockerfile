# ---------- Build stage ----------
FROM maven:3.9-amazoncorretto-17 AS build
WORKDIR /app

# Download dependencies first (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Build the jar
COPY src ./src
RUN mvn -q package -DskipTests

# ---------- Run stage ----------
FROM amazoncorretto:17-alpine
WORKDIR /app

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]