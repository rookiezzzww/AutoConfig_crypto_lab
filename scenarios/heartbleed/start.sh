#!/bin/sh
set -eu
# OpenSSL 1.0.1f is intentionally retained only inside this isolated teaching container.
case "${HEARTBLEED_TLS_PROFILE:-tls1_2}" in
  tls1) tls_flag="-tls1" ;;
  tls1_1) tls_flag="-tls1_1" ;;
  tls1_2) tls_flag="-tls1_2" ;;
  *) echo "Invalid HEARTBLEED_TLS_PROFILE" >&2; exit 64 ;;
esac
exec openssl s_server -accept 8443 -cert cert.pem -key key.pem -www "$tls_flag"
