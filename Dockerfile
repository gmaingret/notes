# Multi-stage build
# NOTE: The React client (client/) must be scaffolded before running a full Docker build.
# Phase 1 Plan 04 creates the client scaffold. Until then, docker build will fail at
# the client stage. The Phase 1 verification checkpoint (Plan 05) verifies the full build.

FROM node:22-alpine AS base
WORKDIR /app

# --- Server dependencies ---
COPY server/package*.json ./server/
RUN cd server && npm ci

# --- Client dependencies and build ---
COPY client/package*.json ./client/
RUN cd client && npm ci
COPY client/ ./client/
RUN cd client && npm run build

# --- Server TypeScript build ---
COPY server/ ./server/
RUN cd server && npx tsc

# --- Production image ---
FROM node:22-alpine
WORKDIR /app

COPY --from=base /app/server/dist ./server/dist
COPY --from=base /app/server/node_modules ./server/node_modules
COPY --from=base /app/server/db ./server/db
COPY --from=base /app/client/dist ./public

EXPOSE 3000

CMD ["node", "server/dist/src/index.js"]
