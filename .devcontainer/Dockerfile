# syntax=docker/dockerfile:1

ARG VARIANT="bullseye"
FROM mcr.microsoft.com/devcontainers/base:${VARIANT}

ARG GRADLE_VERSION="8.7"
ARG JAVA_VERSION="21.0.5-tem"

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        zip \
        unzip \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV SDKMAN_DIR="/home/vscode/.sdkman" \
    GRADLE_VERSION=${GRADLE_VERSION} \
    JAVA_VERSION=${JAVA_VERSION}

USER vscode

RUN curl -s "https://get.sdkman.io" | bash \
    && source "$SDKMAN_DIR/bin/sdkman-init.sh" \
    && sdk install java "$JAVA_VERSION" \
    && sdk install gradle "$GRADLE_VERSION" \
    && sdk flush archives \
    && sdk flush temp

ENV PATH="$SDKMAN_DIR/candidates/gradle/current/bin:$SDKMAN_DIR/candidates/java/current/bin:$PATH" \
    JAVA_HOME="$SDKMAN_DIR/candidates/java/current" \
    GRADLE_HOME="$SDKMAN_DIR/candidates/gradle/current"

WORKDIR /workspaces/java

