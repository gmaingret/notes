from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    google_client_id: str = ""
    google_client_secret: str = ""
    jwt_secret: str = "dev-secret-change-in-production"
    jwt_algorithm: str = "HS256"
    jwt_expiry_hours: int = 24
    jwt_refresh_grace_hours: int = 48

    db_path: str = "/data/notes.db"
    attachments_path: str = "/data/files"
    web_build_path: str = "/app/web"

    cors_origins: list[str] = ["*"]


settings = Settings()
