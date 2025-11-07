FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

RUN useradd -r -u 1001 appuser && mkdir /app

COPY target/jeap-cli /app/jeap-cli

RUN chmod +x /app/${BIN_NAME} && ln -s /app/${BIN_NAME} /usr/local/bin/jeap-cli

USER appuser
WORKDIR /app

ENTRYPOINT ["/app/jeap-cli"]
