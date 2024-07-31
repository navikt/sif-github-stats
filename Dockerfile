FROM gcr.io/distroless/java21-debian12:latest
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar /app/app.jar
WORKDIR /app
CMD [ "app.jar" ]
