# Stage 1: Build the client from source using a secure Go version
FROM golang:1.24-alpine AS builder

# Install git and make
RUN apk add --no-cache git make

WORKDIR /build

# Clone the v2.7.5 source code
RUN git clone --depth 1 --branch v2.7.5 https://github.com/influxdata/influx-cli.git .

# Build the CLI using the Makefile
# This creates the binary at: bin/linux/<arch>/influx
RUN make influx && cp bin/linux/$(go env GOARCH)/influx /build/influx

# Stage 2: Create the final runtime image
FROM ubuntu:latest

RUN apt-get update && \
    apt-get install -y curl tar bash && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /backup
WORKDIR /backup

# FIX: Update the source path to match where 'make' outputs the binary
COPY --from=builder /build/influx /usr/local/bin/influx

RUN chmod +x /usr/local/bin/influx && \
    chmod -R 777 /backup

CMD ["tail", "-f", "/dev/null"]
