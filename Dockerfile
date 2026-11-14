FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl server -am verify

FROM debian:bookworm-slim

RUN groupadd --system --gid 10001 thyme \
    && useradd --system --uid 10001 --gid thyme --no-create-home --shell /usr/sbin/nologin thyme \
    && install -d --owner=10001 --group=10001 --mode=0700 /run/thyme-jna
WORKDIR /opt/thyme
COPY --from=build --chown=10001:10001 /workspace/server/target/thyme-server-runtime /opt/thyme/runtime

ENV THYME_HOST_ROOT=/host
USER 10001:10001
ENTRYPOINT ["/opt/thyme/runtime/bin/java", "-Djna.tmpdir=/run/thyme-jna", "--enable-native-access=com.sun.jna", "--module", "com.ricky.thyme.server/com.ricky.thyme.server.ServerMain"]
