# Multi-stage build: compile with a full JDK, run on a slim JRE.
#
# The build stage copies only the Gradle files first so that dependency
# resolution is cached in its own layer — editing application source does not
# re-download the world.

FROM eclipse-temurin:24-jdk AS build

WORKDIR /workspace

# Dependency layer. Copying the wrapper and build scripts separately means this
# layer is reused until the dependency graph actually changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

# Source layer.
COPY src ./src
RUN ./gradlew --no-daemon installDist

# ---------------------------------------------------------------------------

FROM eclipse-temurin:24-jre

# Run as a non-root user. A container that only ever needs to read its own
# files should not be able to write to them.
RUN useradd --create-home --shell /usr/sbin/nologin appuser
WORKDIR /app

COPY --from=build /workspace/build/install/*/ /app/

USER appuser
EXPOSE 8080

# Healthcheck hits the real endpoint, so an unhealthy dependency surfaces as an
# unhealthy container rather than a container that is up but useless.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD ["sh", "-c", "wget -qO- http://127.0.0.1:8080/api/v1/health || exit 1"]

ENTRYPOINT ["/app/bin/linguaai-server"]
