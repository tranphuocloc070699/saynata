# Stage 1: build the jar with the Maven wrapper (needs a JDK).
# Not the -alpine tags: they have no arm64 build, so they fail on Apple Silicon and ARM servers.
FROM eclipse-temurin:17-jdk AS build
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
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a normal user, not root.
# curl is for the compose healthcheck. ffmpeg makes the small WebP photos and MP4 clips (TASK-49.4).
# The fixed id 10001 lets the host folder for the media volume be owned by the same user (doc-11).
RUN apt-get update && apt-get install -y --no-install-recommends curl ffmpeg && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app \
    && mkdir /app/media && chown app:app /app/media
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app

# Media files (photos and clips). In production a volume is mounted here and Caddy serves it.
ENV MEDIA_DIR=/app/media

ENV PORT=8081
EXPOSE 8081

# Keep the heap small for a 1-2 GB VPS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
