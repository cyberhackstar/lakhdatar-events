# Hardened NGINX edge image: non-root runtime, read-only compatible.
FROM nginx:1.27-alpine

# Apply current Alpine security updates before switching to the non-root runtime user.
RUN apk upgrade --no-cache \
    && sed -i -E \
       -e '/^[[:space:]]*user[[:space:]]+nginx;[[:space:]]*$/d' \
       -e 's|^[[:space:]]*pid[[:space:]]+[^;]+;|pid /tmp/nginx.pid;|' \
       /etc/nginx/nginx.conf \
    && mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp \
        /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp \
        /var/cache/nginx/scgi_temp \
    && chown -R nginx:nginx /var/cache/nginx \
    && chown -R nginx:nginx /etc/nginx \
    && grep -Fq 'pid /tmp/nginx.pid;' /etc/nginx/nginx.conf \
    && ! grep -Eq '^[[:space:]]*user[[:space:]]+nginx;' /etc/nginx/nginx.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY edge-entrypoint.sh /usr/local/bin/edge-entrypoint.sh
RUN chmod 0555 /usr/local/bin/edge-entrypoint.sh \
    && chown nginx:nginx /usr/local/bin/edge-entrypoint.sh

# NGINX runs as the built-in unprivileged user.
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/edge-health >/dev/null || exit 1

ENTRYPOINT ["/usr/local/bin/edge-entrypoint.sh"]
