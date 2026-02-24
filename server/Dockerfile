FROM python:3.12-slim

WORKDIR /app

# System deps (SQLite shared library)
RUN apt-get update && apt-get install -y --no-install-recommends \
        libsqlite3-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY app/ app/

# Copy Flutter web build.
# Build it first with: cd client && flutter build web --release
# Output lands in client/build/web/. The docker-compose build context is the
# repo root so the path below resolves correctly.
# If the directory is absent (dev-only server startup) the app still runs —
# the StaticFiles mount in main.py checks path.exists() before mounting.
COPY client/build/web/ /app/web/

# Persistent data directory — mounted as a Docker volume in production
RUN mkdir -p /data/files

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
