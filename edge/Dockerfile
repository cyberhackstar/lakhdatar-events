# Hardened NGINX edge image: non-root runtime, read-only compatible.
FROM nginx:1.27-alpine

# Apply current Alpine security updates before switching to the non-root runtime user.
RUN apk upgrade --no-cache

# Prepare writable runtime/cache paths for the official nginx UID/GID (101:101).
RUN mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp \
        /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp \
        /var/cache/nginx/scgi_temp \
    && chown -R nginx:nginx /var/cache/nginx \
    && chown -R nginx:nginx /etc/nginx

COPY nginx.conf /etc/nginx/conf.d/default.conf

# NGINX runs as the built-in unprivileged user.
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/edge-health >/dev/null || exit 1

# Bypass the root-oriented image entrypoint and run NGINX directly as nginx.
# Put the PID in /tmp so the image works with a read-only root filesystem.
ENTRYPOINT ["nginx", "-g", "daemon off; pid /tmp/nginx.pid;"]
