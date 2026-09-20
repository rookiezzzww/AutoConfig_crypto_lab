FROM debian:bookworm-slim AS build
ARG OPENSSL_VERSION=1.0.1f
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update -o Acquire::Retries=5 -o Acquire::http::Timeout=30 \
    && apt-get install -y --no-install-recommends --fix-missing ca-certificates curl build-essential perl \
    && rm -rf /var/lib/apt/lists/*
COPY openssl-1.0.1f.tar.gz /tmp/openssl.tar.gz
RUN set -eu; \
        mkdir -p /tmp/openssl-src; \
        tar -xzf /tmp/openssl.tar.gz -C /tmp/openssl-src --strip-components=1; \
        test -f /tmp/openssl-src/Configure; \
        cd /tmp/openssl-src \
    && CFLAGS=-fcommon ./config --prefix=/opt/openssl --openssldir=/opt/openssl shared \
    && make depend \
    && make -j"$(nproc)" build_libs openssl.pc libssl.pc libcrypto.pc \
    && make -C apps -j"$(nproc)" all \
    && make install_sw
FROM debian:bookworm-slim
COPY --from=build /opt/openssl /opt/openssl
COPY start.sh /usr/local/bin/start.sh
RUN groupadd --system lab && useradd --system --gid lab --home-dir /nonexistent lab \
    && mkdir -p /opt/lab && cd /opt/lab \
    && LD_LIBRARY_PATH=/opt/openssl/lib /opt/openssl/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -keyout key.pem -out cert.pem -subj '/CN=heartbleed.lab' \
    && chown -R lab:lab /opt/lab \
    && chmod 0555 /usr/local/bin/start.sh
ENV PATH=/opt/openssl/bin:$PATH LD_LIBRARY_PATH=/opt/openssl/lib RANDFILE=/tmp/.rnd
WORKDIR /opt/lab
USER lab
EXPOSE 8443
ENTRYPOINT ["/usr/local/bin/start.sh"]
