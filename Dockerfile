# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Resolve dependencies before copying source — layer cached until pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Detect required JDK modules from the fat JAR, then build a stripped JRE.
# jdeps finds module deps; jdk.unsupported is added for reflection safety (picocli).
RUN MODULES=$(${JAVA_HOME}/bin/jdeps \
        --ignore-missing-deps \
        --print-module-deps \
        --multi-release 21 \
        target/aggregator.jar) && \
    ${JAVA_HOME}/bin/jlink \
        --module-path "${JAVA_HOME}/jmods" \
        --add-modules "${MODULES},jdk.unsupported" \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=zip-6 \
        --output /opt/jre-minimal

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM alpine:3.19

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /opt/jre-minimal /opt/jre
COPY --from=builder /build/target/aggregator.jar /app/aggregator.jar

RUN chown -R app:app /app

USER app
WORKDIR /data

ENV PATH="/opt/jre/bin:$PATH"

# JVM uses container memory limits automatically (Java 21 default).
# MaxRAMPercentage=75 leaves headroom for OS + off-heap buffers.
# Override per-run: docker run -e JAVA_TOOL_OPTIONS="-Xmx4g" ...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "/app/aggregator.jar"]
