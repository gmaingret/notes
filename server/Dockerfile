# ── Stage 1: Build Flutter web ────────────────────────────────────────────────
FROM ghcr.io/cirruslabs/flutter:stable AS flutter-builder

ARG GOOGLE_CLIENT_ID

WORKDIR /client
COPY client/ .
RUN flutter pub get && \
    dart run build_runner build --delete-conflicting-outputs && \
    curl -fL https://github.com/simolus3/sqlite3.dart/releases/download/sqlite3-2.9.4/sqlite3.wasm \
         -o web/sqlite3.wasm && \
    curl -fL https://github.com/simolus3/drift/releases/download/drift-2.28.2/drift_worker.js \
         -o web/drift_worker.js && \
    flutter build web --release \
      --dart-define=GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID} \
      --dart-define=API_BASE_URL=

# ── Stage 2: Python / FastAPI backend ─────────────────────────────────────────
FROM python:3.12-slim

WORKDIR /app

# System deps (SQLite shared library)
RUN apt-get update && apt-get install -y --no-install-recommends \
        libsqlite3-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY server/app/ app/

# Copy Flutter web build from stage 1
COPY --from=flutter-builder /client/build/web/ /app/web/

# Persistent data directory — mounted as a Docker volume in production
RUN mkdir -p /data/files

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
