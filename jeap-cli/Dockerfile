FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

RUN useradd -r -u 1001 -m -d /home/jeapcli jeapcli

ENV HOME=/home/jeapcli
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"

COPY target/jeap-cli /app/jeap-cli

RUN chmod +x /app/jeap-cli && ln -s /app/jeap-cli /usr/local/bin/jeap-cli

USER jeapcli

# org.jline.terminal.dumb disables the warning in case a terminal is not detected (https://github.com/jline/jline3/issues/291)
ENTRYPOINT ["/app/jeap-cli", "-Dorg.jline.terminal.dumb=true"]
