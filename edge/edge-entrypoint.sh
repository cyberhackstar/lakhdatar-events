#!/bin/sh
set -eu

# The production/HA Compose profile mounts /var/cache/nginx as an empty tmpfs at runtime.
# Create the bounded public micro-cache directory before NGINX parses proxy_cache_path.
mkdir -p /var/cache/nginx/public-cache

# Validate the complete NGINX configuration before starting the master process.
# Compose starts edge only after backend/web are healthy, so their DNS names must resolve here.
if ! nginx -t; then
  echo "edge: nginx configuration validation failed; refusing to start" >&2
  exit 78
fi

exec nginx -g 'daemon off;'
