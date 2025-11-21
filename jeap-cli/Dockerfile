FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

# Proxy configuration for building behind corporate proxies
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

# Install Maven
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA=bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978
ARG MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz

RUN --mount=type=secret,id=cacert,target=/etc/host-ca-bundle.crt \
    if [ -f /etc/host-ca-bundle.crt ]; then \
        echo "Adding host CA certificates to trust store..."; \
        cat /etc/host-ca-bundle.crt >> /etc/ssl/certs/ca-certificates.crt; \
    fi && \
    if [ -n "$HTTP_PROXY" ]; then \
        echo "Acquire::http::Proxy \"$HTTP_PROXY\";" > /etc/apt/apt.conf.d/proxy.conf; \
    fi && \
    if [ -n "$HTTPS_PROXY" ]; then \
        echo "Acquire::https::Proxy \"$HTTPS_PROXY\";" >> /etc/apt/apt.conf.d/proxy.conf; \
    fi && \
    apt-get update && apt-get install -y --no-install-recommends curl && \
    HTTP_PROXY=$HTTP_PROXY HTTPS_PROXY=$HTTPS_PROXY NO_PROXY=$NO_PROXY \
    curl -fsSL -o /tmp/apache-maven.tar.gz ${MAVEN_URL} && \
    echo "${MAVEN_SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - && \
    tar -xzf /tmp/apache-maven.tar.gz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    rm -rf /tmp/* && \
    apt-get remove -y curl && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /etc/apt/apt.conf.d/proxy.conf

ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

RUN useradd -r -u 1001 -m -d /home/jeapcli jeapcli && \
    mkdir -p /home/jeapcli/.m2 && \
    chown -R jeapcli:jeapcli /home/jeapcli && \
    chmod -R 777 /home/jeapcli # Necessary because the CLI will run using the docker host user's uid/gid

ENV HOME=/home/jeapcli
ENV MAVEN_USER_HOME=/home/jeapcli/.m2
ENV MAVEN_OPTS="-Duser.home=/home/jeapcli"
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow --add-opens=java.base/sun.misc=ALL-UNNAMED"

COPY target/jeap-cli /app/jeap-cli

RUN chmod +x /app/jeap-cli && ln -s /app/jeap-cli /usr/local/bin/jeap-cli

USER jeapcli

# org.jline.terminal.dumb disables the warning in case a terminal is not detected (https://github.com/jline/jline3/issues/291)
ENTRYPOINT ["/app/jeap-cli", "-Dorg.jline.terminal.dumb=true", "-Duser.home=/home/jeapcli"]
