FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.10.19.1216Z
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
