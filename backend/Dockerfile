# Stage 1: build the jar with the Maven wrapper (needs a JDK).
# Not the -alpine tags: they have no arm64 build, so they fail on Apple Silicon and ARM servers.
# Both base images are pinned by digest. Docker Hub is slow from the VPS (about 0.4 MB/s), so a new upstream
# image made a deploy pass its time limit (2026-10-18). To upgrade: docker pull the tag, then copy its digest here.
FROM eclipse-temurin:17-jdk@sha256:fe947d993c32dd9cf651a4353a6ed723c2370b3a9d034ea4cab4775404ea531a AS build
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
FROM eclipse-temurin:17-jre@sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8
WORKDIR /app

# Run as a normal user, not root.
# curl is for the compose healthcheck. ffmpeg makes the small WebP photos and MP4 clips (TASK-49.4).
# The fixed id 10001 lets the host folder for the media volume be owned by the same user (doc-11).
RUN apt-get update && apt-get install -y --no-install-recommends curl ffmpeg && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app \
    && mkdir /app/media && chown app:app /app/media

# The Codex CLI draws the AI pictures the admin asks for (TASK-91, doc-11 "AI pictures with Codex"). It is the official
# static release binary from GitHub (no Node.js needed), plus codex-code-mode-host, which `codex exec` starts
# next to itself (without it a draw fails with "Failed to spawn code-mode host"). TARGETARCH is set by Docker: amd64 or arm64.
# The version is pinned; to upgrade, change CODEX_VERSION (check: gh release list -R openai/codex).
ARG TARGETARCH
ARG CODEX_VERSION=0.163.0
RUN case "$TARGETARCH" in \
        amd64) CODEX_ARCH=x86_64 ;; \
        arm64) CODEX_ARCH=aarch64 ;; \
        *) echo "No Codex build for $TARGETARCH" >&2; exit 1 ;; \
    esac \
    && for BIN in codex codex-code-mode-host; do \
        curl -fsSL "https://github.com/openai/codex/releases/download/rust-v${CODEX_VERSION}/${BIN}-${CODEX_ARCH}-unknown-linux-musl.tar.gz" \
            | tar -xz -C /usr/local/bin \
        && mv "/usr/local/bin/${BIN}-${CODEX_ARCH}-unknown-linux-musl" "/usr/local/bin/${BIN}" \
        && chmod 755 "/usr/local/bin/${BIN}" || exit 1; \
    done \
    && codex --version

# The Codex login (auth.json) lives in a volume mounted here; the folder must be writable because Codex refreshes
# the tokens in it. In production the compose file mounts it (CODEX_HOST_DIR).
RUN mkdir /app/.codex && chown app:app /app/.codex
ENV CODEX_HOME=/app/.codex

COPY --from=build --chown=app:app /build/app.jar app.jar
USER app

# Media files (photos and clips). In production a volume is mounted here and Caddy serves it.
ENV MEDIA_DIR=/app/media

ENV PORT=8081
EXPOSE 8081

# Keep the heap small for a 1-2 GB VPS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
