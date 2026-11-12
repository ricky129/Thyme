FROM eclipse-temurin:25-jre

RUN groupadd --system --gid 10001 thyme \
    && useradd --system --uid 10001 --gid thyme --no-create-home --shell /usr/sbin/nologin thyme
WORKDIR /opt/thyme
COPY --chown=10001:10001 thyme.jar /opt/thyme/thyme.jar

ENV THYME_HOST_ROOT=/host
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/opt/thyme/thyme.jar"]
