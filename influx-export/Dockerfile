FROM ubuntu:latest

RUN apt-get update && \
    apt-get install -y curl tar bash


RUN mkdir -p /backup && chmod 777 /backup
WORKDIR /backup


RUN curl -O \
    https://dl.influxdata.com/influxdb/releases/influxdb2-client-2.7.5-linux-amd64.tar.gz && \
    tar xvzf influxdb2-client-2.7.5-linux-amd64.tar.gz




CMD ["tail", "-f", "/dev/null"]