FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

RUN useradd -r -u 1001 -m -d /home/jeapcli jeapcli

ENV HOME=/home/jeapcli

COPY target/jeap-cli /app/jeap-cli

RUN chmod +x /app/jeap-cli && ln -s /app/jeap-cli /usr/local/bin/jeap-cli

USER jeapcli

ENTRYPOINT ["/app/jeap-cli"]
