FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY cardbattle-frontend/package*.json ./
RUN npm ci
COPY cardbattle-frontend/ ./
RUN npm run build:standalone

FROM node:22-alpine AS server-build
WORKDIR /build/server
COPY cardbattle-server/package*.json ./
RUN npm ci
COPY cardbattle-server/ ./
RUN npm run build

FROM node:22-alpine AS runtime
ENV NODE_ENV=production \
    CARDBATTLE_HOST=0.0.0.0 \
    CARDBATTLE_PORT=8787 \
    CARDBATTLE_DATA_DIR=/data \
    CARDBATTLE_FRONTEND_DIR=/app/public
WORKDIR /app
COPY cardbattle-server/package*.json ./
RUN npm ci --omit=dev
COPY --from=server-build /build/server/dist ./dist
COPY cardbattle-server/public/card-art ./public/card-art
COPY --from=frontend /build/frontend/dist ./public
RUN mkdir -p /data
EXPOSE 8787
VOLUME ["/data"]
USER node
CMD ["node", "dist/index.js"]
