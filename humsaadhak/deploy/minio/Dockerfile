# MinIO (the document storage), built from its published source. MinIO stopped publishing free Docker images of its
# community edition in October 2025 (minio/minio on Docker Hub is gone) and now releases source only; this is the
# same server with the same settings as before, built the way MinIO's own Makefile does.
FROM golang:1.24-bookworm AS build
ARG MINIO_RELEASE=RELEASE.2025-10-15T17-29-55Z
# The release's commit: the build stops if the downloaded source is anything else
ARG MINIO_COMMIT=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ENV CGO_ENABLED=0 GOFLAGS=-trimpath
WORKDIR /src
# Source and Go modules are retried (a slow connection should not end the build) and modules stay in a build cache
RUN for i in 1 2 3 4 5; do git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$MINIO_RELEASE" https://github.com/minio/minio.git minio && break; \
      rm -rf minio; echo "retrying the download of the MinIO source ($i)"; sleep 10; done \
 && test "$(git -C minio rev-parse HEAD)" = "$MINIO_COMMIT"
WORKDIR /src/minio
RUN --mount=type=cache,target=/go/pkg/mod \
    for i in 1 2 3 4 5; do go mod download && break; echo "retrying the Go module downloads ($i)"; sleep 10; done
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    version=$(echo "$MINIO_RELEASE" | sed -E 's/^RELEASE\.([0-9-]+)T([0-9]+)-([0-9]+)-([0-9]+)Z$/\1T\2:\3:\4Z/') \
 && go build -tags kqueue -ldflags "-s -w -X github.com/minio/minio/cmd.Version=$version \
      -X github.com/minio/minio/cmd.ReleaseTag=$MINIO_RELEASE -X github.com/minio/minio/cmd.CommitID=$MINIO_COMMIT \
      -X github.com/minio/minio/cmd.ShortCommitID=$(echo "$MINIO_COMMIT" | cut -c1-12)" -o /out/minio . \
 && /out/minio --version

FROM debian:bookworm-slim
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/minio /usr/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
