#!/bin/sh
set -eu
# SSLv3 is deliberately enabled solely for a private POODLE demonstration.
case "${POODLE_PROTOCOL:-ssl3}" in
  ssl3) protocol_flag="-ssl3" ;;
  tls1) protocol_flag="-tls1" ;;
  *) echo "Invalid POODLE_PROTOCOL" >&2; exit 64 ;;
esac
exec openssl s_server -accept 8443 -cert cert.pem -key key.pem -www "$protocol_flag"
