FROM eclipse-temurin:25-jdk-noble

LABEL org.opencontainers.image.description="jEAP CLI Container Image (https://github.com/jeap-admin-ch/jeap-cli)"

# Proxy configuration for building behind corporate proxies
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

# Install Maven
ARG MAVEN_VERSION=3.9.12
ARG MAVEN_SHA=0a1be79f02466533fc1a80abbef8796e4f737c46c6574ede5658b110899942a94db634477dfd3745501c80aef9aac0d4f841d38574373f7e2d24cce89d694f70
ARG MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz

# Note: curl/wget are used by the maven wrapper to download maven at runtime. They are thus part of the distributed image.
# The fallback download mechanism using Java in maven wrapper does not work well with proxies.

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
    apt-get update && apt-get install -y --no-install-recommends curl wget && \
    HTTP_PROXY=$HTTP_PROXY HTTPS_PROXY=$HTTPS_PROXY NO_PROXY=$NO_PROXY \
    curl -fsSL -o /tmp/apache-maven.tar.gz ${MAVEN_URL} && \
    echo "${MAVEN_SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - && \
    tar -xzf /tmp/apache-maven.tar.gz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    rm -rf /tmp/* && \
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
