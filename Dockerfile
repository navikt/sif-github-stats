FROM ghcr.io/navikt/sif-baseimages/java-chainguard-25:2026.11.06.1317Z
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
