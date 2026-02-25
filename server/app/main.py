import asyncio
import logging
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config import settings
from app.db.database import close_db, get_db, init_db
from app.routers import attachments, auth, bullets, documents, sync

logger = logging.getLogger(__name__)

_cleanup_task: asyncio.Task | None = None


async def _cleanup_loop() -> None:
    """
    Background task: hard-delete soft-deleted rows older than 60 seconds.
    Runs every 30 seconds.
    """
    while True:
        await asyncio.sleep(30)
        try:
            db = await get_db()
            cutoff = int((time.time() - 60) * 1000)
            await db.execute(
                "DELETE FROM bullets WHERE deleted_at IS NOT NULL AND deleted_at < ?",
                (cutoff,),
            )
            await db.execute(
                "DELETE FROM documents WHERE deleted_at IS NOT NULL AND deleted_at < ?",
                (cutoff,),
            )
            await db.execute(
                "DELETE FROM attachments WHERE deleted_at IS NOT NULL AND deleted_at < ?",
                (cutoff,),
            )
            await db.commit()
        except Exception:
            logger.exception("Cleanup task error")


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cleanup_task
    await init_db()
    _cleanup_task = asyncio.create_task(_cleanup_loop())
    yield
    if _cleanup_task is not None:
        _cleanup_task.cancel()
        try:
            await _cleanup_task
        except asyncio.CancelledError:
            pass
    await close_db()


app = FastAPI(
    title="Notes API",
    description="Self-hosted personal outliner API",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(documents.router)
app.include_router(bullets.router)
app.include_router(sync.router)
app.include_router(attachments.router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


# Serve Flutter web build if the directory exists
web_path = Path(settings.web_build_path)
if web_path.exists():
    app.mount("/", StaticFiles(directory=str(web_path), html=True), name="web")
