"""DT2-28: native Flyway, owned MySQL *_test, real Spring Boot and browser E2E.

Requires TEST_DB_URL/USERNAME/PASSWORD, MYSQL_BIN and a freshly built backend JAR/classpath.
No shared schema writes. Raw test output stays in ignored backend/target/dt2-28-browser.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend/target/dt2-28-browser"
DDL_SHA = "84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sql(statement, schema=None):
    url = urllib.parse.urlsplit(os.environ["TEST_DB_URL"].removeprefix("jdbc:"))
    require(url.hostname in ("localhost", "127.0.0.1") and url.path.endswith("_test"), "Explicit loopback test database required")
    if schema:
        require(re.fullmatch(r"smart_cafe_dt2_28_browser_[a-f0-9]{12}_test", schema), "Not an owned browser test schema")
        statement = f"USE `{schema}`; SET SESSION time_zone='+00:00'; " + statement
    run = subprocess.run([os.environ["MYSQL_BIN"], "--protocol=TCP", "-h" + url.hostname, "-P" + str(url.port or 3306),
        "-u" + os.environ["TEST_DB_USERNAME"], "--batch", "--skip-column-names", "--default-character-set=utf8mb4"],
        input=statement, text=True, encoding="utf-8", capture_output=True,
        env={**os.environ, "MYSQL_PWD": os.environ["TEST_DB_PASSWORD"]})
    require(run.returncode == 0, "Test SQL failed; raw configuration withheld")
    return run.stdout.strip()


def sql_helper():
    schema = os.environ["PROFILE_LIVE_SCHEMA"]
    operation, account = sys.argv[2:]
    account_id = {"admin": 101, "staff": 103, "expired": 107, "customer": 105}[account]
    if operation == "snapshot":
        result = sql("SELECT JSON_OBJECT('name',u.full_name,'phone',u.phone,'address',p.address,"
            "'version',CAST(u.version AS CHAR),'authVersion',CAST(u.auth_version AS CHAR),"
            "'mustChangePassword',u.must_change_password,'passwordChangedAt',CAST(u.password_changed_at AS CHAR)) "
            f"FROM users u LEFT JOIN staff_profiles p ON p.user_id=u.id WHERE u.id={account_id}", schema)
        print(json.dumps(json.loads(result), ensure_ascii=True))
    elif operation == "concurrent" and account == "admin":
        sql("START TRANSACTION; SELECT id FROM users WHERE id=101 FOR UPDATE; "
            "UPDATE users SET full_name='Concurrent synthetic owner',version=version+1 WHERE id=101; COMMIT;", schema)
        print('{"updated":true}')
    else:
        raise RuntimeError("Unsupported fixture operation")


def free_port():
    with socket.socket() as reserve:
        reserve.bind(("127.0.0.1", 0))
        return reserve.getsockname()[1]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    input_url = urllib.parse.urlsplit(os.environ["TEST_DB_URL"].removeprefix("jdbc:"))
    require(input_url.scheme == "mysql" and input_url.hostname in ("localhost", "127.0.0.1")
        and re.fullmatch(r"/[A-Za-z0-9_]+_test", input_url.path) and not input_url.username and not input_url.password,
        "Explicit loopback TEST_DB_URL ending in _test required")
    ddl = ROOT / "backend/src/main/resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql"
    require(hashlib.sha256(ddl.read_bytes()).hexdigest() == DDL_SHA, "Canonical DDL drift; hold for DBA")
    schema = "smart_cafe_dt2_28_browser_" + secrets.token_hex(6) + "_test"
    url = f"jdbc:mysql://{input_url.hostname}:{input_url.port or 3306}/{schema}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    password, new_password = "Aa1!" + secrets.token_hex(18), "Bb2!" + secrets.token_hex(18)
    classpath = str(ROOT / "backend/target/classes") + os.pathsep + (ROOT / "backend/target/dt2-3-classpath.txt").read_text().strip()
    env = {**os.environ, "TEST_DB_URL": url, "PROFILE_LIVE_SCHEMA": schema, "PROFILE_LIVE_PASSWORD": password,
        "PROFILE_LIVE_NEW_PASSWORD": new_password, "PYTHON_EXECUTABLE": sys.executable, "NO_PROXY": "127.0.0.1,localhost"}
    created, server, log = False, None, None
    result = {"revision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "executedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "schema": schema,
        "ddlVersion": "20261113.1", "ddlSha256": DDL_SHA, "mockedEndpoints": 0}
    sources = [path for directory in ("frontend/src", "frontend/tests", "frontend/e2e-live", "frontend/unit")
        for path in (ROOT / directory).rglob("*") if path.is_file()]
    sources += [ROOT / "frontend/package.json", ROOT / "frontend/playwright.account-live.config.js", Path(__file__)]
    result["sourceManifest"] = {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources}
    try:
        sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
        created = True
        with (OUT / "native-flyway.log").open("w", encoding="utf-8") as native_log:
            native = subprocess.run(["java", "-cp", classpath, "vn.codegym.smartcafe.database.DatabaseRehearsal", "provision"],
                cwd=ROOT, env=env, stdout=native_log, stderr=subprocess.STDOUT)
        require(native.returncode == 0, "Native Flyway/preflight failed")
        result["nativeFlyway"] = "provision/validate/repeat PASS"
        result["mysqlVersion"] = sql("SELECT VERSION()", schema)
        hashed = subprocess.run(["java", "--class-path", classpath, str(ROOT / "scripts/ProfileFixturePassword.java")],
            input=password + "\n", text=True, capture_output=True, env=env)
        require(hashed.returncode == 0 and hashed.stdout.strip().startswith("$2"), "Fixture BCrypt failed")
        sql("SET @profile_fixture_hash='" + hashed.stdout.strip() + "'; "
            + (ROOT / "docs/qa/postman/dt2-3-test-fixture.sql").read_text(encoding="utf-8")
            + "; UPDATE media_assets SET deleted_at=UTC_TIMESTAMP(6) WHERE id=1001;", schema)
        port = free_port()
        base = f"http://127.0.0.1:{port}"
        jar = ROOT / "backend/target/smart-cafe-0.0.1-SNAPSHOT.jar"
        result["backendJarSha256"] = hashlib.sha256(jar.read_bytes()).hexdigest()
        log = (OUT / "spring.log").open("w", encoding="utf-8")
        server = subprocess.Popen(["java", "-Xmx512m", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar", str(jar),
            "--server.address=127.0.0.1", f"--server.port={port}", "--spring.profiles.active=verified-db"], cwd=ROOT,
            env={**env, "DB_URL": url, "DB_USERNAME": env["TEST_DB_USERNAME"], "DB_PASSWORD": env["TEST_DB_PASSWORD"],
                "AUTH_TOKEN_SECRET": secrets.token_hex(48)}, stdout=log, stderr=subprocess.STDOUT,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            require(server.poll() is None, "Owned backend exited; inspect private Spring log")
            try:
                with urllib.request.urlopen(base + "/actuator/health", timeout=1) as health:
                    if health.status == 200:
                        break
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(.25)
        else:
            raise RuntimeError("Owned backend startup timeout")
        env.update({"PROFILE_LIVE_API_URL": base, "VITE_API_PROXY_TARGET": base})
        with (OUT / "playwright.log").open("w", encoding="utf-8") as browser_log:
            browser = subprocess.run(["cmd.exe", "/c", "npx", "playwright", "test", "--config=playwright.account-live.config.js"],
                cwd=ROOT / "frontend", env=env, stdout=browser_log, stderr=subprocess.STDOUT)
        result["browserExitCode"] = browser.returncode
        report = json.loads((OUT / "playwright.json").read_text(encoding="utf-8"))
        result["browserStats"] = report["stats"]
        require(browser.returncode == 0, "Browser E2E failed; inspect private Playwright log")
    finally:
        if server:
            server.terminate()
            try:
                server.wait(timeout=20)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=10)
        if log:
            log.close()
        if created:
            sql(f"DROP DATABASE `{schema}`")
            result["ownedSchemaRemoved"] = True
        (OUT / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({key: value for key, value in result.items() if key != "sourceManifest"}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "sql":
        sql_helper()
    else:
        main()
