# Stage 1: build the jar with the Maven wrapper (needs a JDK).
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build

# Copy only what Maven needs to download dependencies first, so this layer is cached
# until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI. The image build only packages the jar.
RUN ./mvnw -B -q package -DskipTests && mv target/backend-*.jar app.jar

# Stage 2: a small runtime with only a JRE.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as a normal user, not root.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app

ENV PORT=8081
EXPOSE 8081

# Keep the heap small for a 1-2 GB VPS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
