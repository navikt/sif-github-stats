FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.08.03.0816Z
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
