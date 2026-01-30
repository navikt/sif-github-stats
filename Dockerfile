FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.01.29.1157Z
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
