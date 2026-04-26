from __future__ import annotations

from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


def _default_sqlite_path() -> Path:
    # herb_review_system/api/app/config.py -> parents[2] == herb_review_system
    return Path(__file__).resolve().parents[2] / "data" / "herb_review.db"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    api_v1_prefix: str = Field(default="/api/v1", validation_alias="API_V1_PREFIX")
    api_root_path: str = Field(default="", validation_alias="API_ROOT_PATH")

    database_url: str | None = Field(default=None, validation_alias="DATABASE_URL")
    sqlite_path: Path = Field(default_factory=_default_sqlite_path, validation_alias="SQLITE_PATH")

    cors_origins: str = Field(default="*", validation_alias="CORS_ORIGINS")

    jwt_secret: str = Field(default="change-me", validation_alias="JWT_SECRET")
    jwt_expire_minutes: int = Field(default=60 * 24 * 7, validation_alias="JWT_EXPIRE_MINUTES")
    jwt_algorithm: str = Field(default="HS256", validation_alias="JWT_ALGORITHM")

    dev_allow_header_auth: bool = Field(default=True, validation_alias="DEV_ALLOW_HEADER_AUTH")

    @property
    def sqlalchemy_database_uri(self) -> str:
        if self.database_url:
            return self.database_url
        p = self.sqlite_path.resolve()
        return f"sqlite:///{p.as_posix()}"

    @property
    def cors_origin_list(self) -> list[str]:
        raw = (self.cors_origins or "").strip()
        if not raw or raw == "*":
            return ["*"]
        return [x.strip() for x in raw.split(",") if x.strip()]


settings = Settings()
