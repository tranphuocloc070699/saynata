"""Shared test setup: a real Postgres for the import and export tests.

Set TEST_DATABASE_URL to a Postgres you can write to, for example
    postgresql://postgres@localhost:5432/postgres
Every test gets its own new schema (test_<random>) that is dropped afterwards, so no other data is touched.
The tables come from the backend's Flyway files, so the tests also check that those files work on Postgres.
Without TEST_DATABASE_URL these tests are skipped.
"""

import os
import re
import uuid
from pathlib import Path

import psycopg
import pytest

MIGRATIONS = Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration"


def migration_files() -> list[Path]:
    """V1__..., V2__... in version order (V10 comes after V9)."""
    return sorted(MIGRATIONS.glob("V*.sql"), key=lambda f: int(re.match(r"V(\d+)__", f.name).group(1)))


@pytest.fixture
def database_url() -> str:
    """A connection string whose default schema is new and empty except for the backend tables."""
    base_url = os.environ.get("TEST_DATABASE_URL")
    if not base_url:
        pytest.skip("TEST_DATABASE_URL is not set (needs a Postgres)")
    schema = "test_" + uuid.uuid4().hex[:12]
    with psycopg.connect(base_url, autocommit=True) as admin:
        admin.execute(f"CREATE SCHEMA {schema}")
        url = psycopg.conninfo.make_conninfo(base_url, options=f"-c search_path={schema}")
        with psycopg.connect(url, autocommit=True) as setup:
            for file in migration_files():
                setup.execute(file.read_text())
        yield url
        admin.execute(f"DROP SCHEMA {schema} CASCADE")


@pytest.fixture
def pg(database_url):
    with psycopg.connect(database_url) as connection:
        yield connection
