#!/bin/sh
set -eu

# Validate the complete NGINX configuration before starting the master process.
# Compose starts edge only after backend/web are healthy, so their DNS names must resolve here.
if ! nginx -t; then
  echo "edge: nginx configuration validation failed; refusing to start" >&2
  exit 78
fi

exec nginx -g 'daemon off;'
