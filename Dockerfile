FROM cgr.dev/chainguard/jre:openjdk17
LABEL org.opencontainers.image.source=https://github.com/navikt/sif-github-stats

COPY build/libs/app.jar app.jar
CMD [ "-jar", "app.jar" ]