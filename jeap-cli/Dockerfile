FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

ENV HOME=/home/jeapcli

RUN useradd -r -u 1001 -m -d /home/jeapcli jeapcli \
    && mkdir /workspace \
    && chown -R jeapcli:jeapcli /workspace

COPY target/jeap-cli /app/jeap-cli

RUN chmod +x /app/jeap-cli && ln -s /app/jeap-cli /usr/local/bin/jeap-cli

USER jeapcli

WORKDIR /workspace

ENTRYPOINT ["/app/jeap-cli"]
