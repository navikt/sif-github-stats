FROM ghcr.io/navikt/sif-baseimages/java-25:2025.12.03.1527z
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
WORKDIR /app
CMD [ "app.jar" ]
