FROM golang:1.23-bookworm AS builder

COPY go.mod go.sum /src/

WORKDIR /src
RUN go mod download

COPY main.go /src/
COPY database /src/database
COPY state /src/state
RUN GOOS=linux GOARCH=amd64 CGO_ENABLED=1 \
  go build -o yellowstone-server main.go

FROM debian:12.9

COPY --from=builder /src/yellowstone-server /usr/local/bin/yellowstone-server
WORKDIR /data

CMD ["/usr/local/bin/yellowstone-server"]

