# Angular SSR web server. Node 24 LTS is the production runtime baseline.
FROM node:26-alpine3.22 AS build
RUN apk upgrade --no-cache
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build \
    && test -s dist/frontend/browser/assets/neelastack-logo.png \
    && test -s dist/frontend/browser/assets/og-default.png \
    && npm prune --omit=dev --no-audit --no-fund

FROM node:26-alpine3.22
RUN apk upgrade --no-cache
ENV NODE_ENV=production PORT=3000 SSR_API_BASE_URL=http://backend:8080/api/v1
WORKDIR /app
COPY --from=build /build/dist/frontend ./dist/frontend
COPY --from=build /build/package*.json ./
# The SSR server externalizes Node dependencies such as Express; install production dependencies in the runtime image.
RUN npm ci --omit=dev --no-audit --no-fund \
    && npm cache clean --force \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
    && chown -R node:node /app
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD wget -qO- http://127.0.0.1:3000/healthz >/dev/null || exit 1
CMD ["node", "dist/frontend/server/server.mjs"]
