#!/bin/sh
set -eu
# DES-CBC3-SHA intentionally demonstrates the 64-bit block cipher condition behind Sweet32.
cipher_value="${SWEET32_CIPHER:-DES-CBC3-SHA}"
case "$cipher_value" in
  DES-CBC3-SHA|AES128-SHA) cipher_suite="$cipher_value" ;;
  *) echo "Invalid SWEET32_CIPHER" >&2; exit 64 ;;
esac
exec openssl s_server -accept 8443 -cert cert.pem -key key.pem -www -cipher "$cipher_suite"
