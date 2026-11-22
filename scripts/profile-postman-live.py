"""Newman CLI + real Spring Boot + verified MySQL; owns and removes one fresh *_test schema.

Inputs: TEST_DB_URL/USERNAME/PASSWORD, MYSQL_BIN, NEWMAN_CLI (Newman 6.2.1 bin/newman.js).
Build first: backend/mvnw verify dependency:build-classpath -Dmdep.outputFile=target/dt2-3-classpath.txt
All raw artifacts stay under ignored backend/target/postman/dt2-3. No shared DB writes.
"""
import datetime
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "backend/target/postman/dt2-3"
COLLECTION = ROOT / "docs/qa/postman/dt2-3-personal-account.postman_collection.json"
SQL_SHA = "84cb9f2e99073ea73b117e828dc7f144928052a8f8345cde6f903b82280e4b5e"


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    url = urllib.parse.urlsplit(os.environ.get("TEST_DB_URL", "").removeprefix("jdbc:"))
    require(url.scheme == "mysql" and url.hostname in ("localhost", "127.0.0.1")
            and re.fullmatch(r"/[A-Za-z0-9_]+_test", url.path) and not url.username and not url.password,
            "Explicit loopback TEST_DB_URL ending in _test required")
    user, password = os.environ["TEST_DB_USERNAME"], os.environ["TEST_DB_PASSWORD"]
    mysql, newman = os.environ["MYSQL_BIN"], os.environ["NEWMAN_CLI"]
    require(json.loads((Path(newman).parent.parent / "package.json").read_text())["version"] == "6.2.1",
            "Use Newman 6.2.1")
    schema = "smart_cafe_dt2_3_" + secrets.token_hex(6) + "_test"
    require(re.fullmatch(r"smart_cafe_dt2_3_[a-f0-9]{12}_test", schema), "Unsafe owned schema name")
    test_url = f"jdbc:mysql://{url.hostname}:{url.port or 3306}/{schema}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    cp_file = ROOT / "backend/target/dt2-3-classpath.txt"
    classpath = str(ROOT / "backend/target/classes") + os.pathsep + cp_file.read_text().strip()
    fixture_password, new_password = "Aa1!" + secrets.token_hex(18), "Bb2!" + secrets.token_hex(18)
    children, logs, phases, checks, examples = [], [], [], [], []
    env_file = OUT / "runtime-environment.json"
    created = False
    result = {"revision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
              "dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
              "executedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "schema": schema,
              "ddlVersion": "20261113.1", "ddlSha256": SQL_SHA, "phases": phases, "databaseChecks": checks,
              "verifiedExamples": examples}

    def sql(statement, selected=True):
        prefix = f"USE `{schema}`; SET SESSION time_zone='+00:00'; " if selected else ""
        command = [mysql, "--protocol=TCP", "-h" + url.hostname, "-P" + str(url.port or 3306),
                   "-u" + user, "--batch", "--skip-column-names", "--default-character-set=utf8mb4"]
        run = subprocess.run(command, input=prefix + statement, text=True, encoding="utf-8", capture_output=True,
                             env={**os.environ, "MYSQL_PWD": password})
        require(run.returncode == 0, "Test SQL failed; details withheld to protect configuration")
        return run.stdout.strip()

    def write(statement):
        require(created and sql("SELECT DATABASE()") == schema and schema.endswith("_test"), "Wrong write catalog")
        return sql(statement)

    def bcrypt(value, encoded=None):
        command = ["java", "--class-path", classpath, str(ROOT / "scripts/ProfileFixturePassword.java")]
        if encoded is not None:
            command.append("matches")
        run = subprocess.run(command, input=value + "\n" + (encoded + "\n" if encoded else ""),
                             capture_output=True, text=True, encoding="utf-8")
        require(run.returncode == 0, "Fixture BCrypt helper failed")
        return run.stdout.strip()

    def snapshot():
        return sql("SELECT id,username,password_hash,full_name,email,IFNULL(phone,''),role,status,"
                   "password_changed_at,must_change_password,auth_version,version FROM users ORDER BY id; "
                   "SELECT user_id,address,IFNULL(birthday,''),IFNULL(gender,''),salary FROM staff_profiles ORDER BY user_id")

    def checked(name, condition):
        checks.append({"check": name, "status": "PASS" if condition else "FAIL"})
        require(condition, "Database verification failed: " + name)

    def start(name, ttl):
        with socket.socket() as reserve:
            reserve.bind(("127.0.0.1", 0))
            port = reserve.getsockname()[1]
        log = (OUT / (name + "-server.log")).open("w", encoding="utf-8")
        logs.append(log)
        process = subprocess.Popen(["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-jar",
            str(ROOT / "backend/target/smart-cafe-0.0.1-SNAPSHOT.jar"), "--server.address=127.0.0.1",
            f"--server.port={port}", "--spring.profiles.active=verified-db",
            "--spring.flyway.enabled=true", "--spring.flyway.validate-on-migrate=true",
            "--spring.flyway.baseline-on-migrate=false", "--spring.sql.init.mode=never"],
            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
            env={**os.environ, "DB_URL": test_url, "DB_USERNAME": user, "DB_PASSWORD": password,
                 "AUTH_TOKEN_SECRET": secrets.token_hex(48), "AUTH_TOKEN_VALIDITY_SECONDS": str(ttl)},
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        children.append(process)
        base = f"http://127.0.0.1:{port}"
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            require(process.poll() is None, "Owned backend exited before health; see private server log")
            try:
                with urllib.request.urlopen(base + "/actuator/health", timeout=1) as response:
                    if response.status == 200:
                        return base
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(.25)
        raise RuntimeError("Owned backend health timeout")

    def run_collection(name, source=COLLECTION, folder=None, expect_failure=False):
        raw = OUT / (name + "-raw.json")
        command = ["node", newman, "run", str(source), "-e", str(env_file), "--export-environment", str(env_file),
                   "--timeout-request", "10000", "--timeout", "180000", "-r", "json", "--reporter-json-export", str(raw)]
        if folder:
            command += ["--folder", folder]
        with (OUT / (name + "-cli.log")).open("w", encoding="utf-8") as log:
            completed = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        report = json.loads(raw.read_text(encoding="utf-8"))
        run = report["run"]
        stats = run["stats"]
        summary = {"name": name, "requests": stats["requests"]["total"], "assertions": stats["assertions"]["total"],
                   "failedAssertions": stats["assertions"]["failed"], "failures": len(run["failures"]),
                   "exitCode": completed.returncode, "negativeControl": expect_failure,
                   "failedItems": [f.get("source", {}).get("name", "script") for f in run["failures"]]}
        phases.append(summary)
        selected = {"Login admin", "Own profile admin", "Update admin own allowed fields", "Change STAFF password"}
        sensitive = {"password", "currentPassword", "newPassword", "confirmNewPassword", "token", "passwordHash", "password_hash", "otp", "resetToken"}
        def sanitized(value):
            if isinstance(value, dict):
                return {key: "<redacted>" if key in sensitive else sanitized(item) for key, item in value.items()}
            if isinstance(value, list):
                return [sanitized(item) for item in value]
            return value
        if not name.startswith("12-"):
            for execution in run["executions"]:
                if execution["item"]["name"] not in selected:
                    continue
                response = execution["response"]
                stream = response["stream"]
                body = json.loads(bytes(stream["data"]).decode("utf-8"))
                request = execution["request"]
                examples.append({"name": execution["item"]["name"], "method": request["method"],
                                 "path": "/" + "/".join(request["url"]["path"]), "status": response["code"],
                                 "request": sanitized(json.loads(request["body"]["raw"])) if request.get("body") else None,
                                 "response": sanitized(body)})
        require(stats["requests"]["failed"] == 0 and summary["assertions"] > 0, "Transport/script setup failure is not valid acceptance evidence")
        if expect_failure:
            require(completed.returncode != 0 and summary["failedAssertions"] > 0
                    and all(f["error"]["name"] == "AssertionError" for f in run["failures"]), "Negative control did not detect a wrong expectation")
        else:
            require(completed.returncode == 0 and not run["failures"], "Newman failed: " + name + "; inspect private raw report")
        print(json.dumps(summary, ensure_ascii=False), flush=True)

    try:
        ddl = ROOT / "backend/src/main/resources/db/smart-cafe/V20261113_01__smart_cafe_schema.sql"
        require(hashlib.sha256(ddl.read_bytes()).hexdigest() == SQL_SHA, "DDL drift")
        jar = ROOT / "backend/target/smart-cafe-0.0.1-SNAPSHOT.jar"
        with zipfile.ZipFile(jar) as packaged:
            for compiled in (ROOT / "backend/target/classes").rglob("*"):
                if compiled.is_file():
                    name = "BOOT-INF/classes/" + compiled.relative_to(ROOT / "backend/target/classes").as_posix()
                    require(packaged.read(name) == compiled.read_bytes(), "Package is stale; run Maven package first")
        result["runtimeJarSha256"] = hashlib.sha256(jar.read_bytes()).hexdigest()
        sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci", False)
        created = True
        result["mysqlVersion"] = sql("SELECT VERSION()")
        modes = sql("SELECT @@SESSION.sql_mode").split(",")
        if "NO_BACKSLASH_ESCAPES" not in modes:
            modes.append("NO_BACKSLASH_ESCAPES")
        require(all(re.fullmatch("[A-Z_]+", mode) for mode in modes), "Unexpected SQL mode")
        # Set mode during connection setup so Connector/J prepares and escapes using the same mode.
        test_url += "&sessionVariables=" + urllib.parse.quote("sql_mode='" + ",".join(modes) + "'", safe="")
        with (OUT / "native-flyway.log").open("w", encoding="utf-8") as log:
            native = subprocess.run(["java", "-cp", classpath, "vn.codegym.smartcafe.database.DatabaseRehearsal", "provision"],
                cwd=ROOT, env={**os.environ, "TEST_DB_URL": test_url}, stdout=log, stderr=subprocess.STDOUT)
        require(native.returncode == 0, "Native verified provision failed")
        result["nativeFlyway"] = {"exitCode": native.returncode, "provision": 1, "repeat": 0, "validate": "PASS"}
        fixture_hash = bcrypt(fixture_password)
        require(fixture_hash.startswith("$2"), "Fixture must use BCrypt")
        write("SET @profile_fixture_hash='" + fixture_hash + "'; " + (ROOT / "docs/qa/postman/dt2-3-test-fixture.sql").read_text(encoding="utf-8"))
        # Existing Menu/QR collections are unchanged and run against the same actual verified server.
        write((ROOT / "docs/qa/postman/dt2-4-menu-test-fixture.sql").read_text(encoding="utf-8"))
        write((ROOT / "docs/qa/postman/dt2-4-qr-test-fixture.sql").read_text(encoding="utf-8"))
        base, expiry_base = start("profile", 86400), start("expiry", 1)
        result["baseUrl"], result["expiryBaseUrl"] = base, expiry_base
        result["jvmTimezone"] = "Asia/Ho_Chi_Minh"
        result["jdbcSessionSqlMode"] = "NO_BACKSLASH_ESCAPES"
        values = {item["key"]: item["value"] for item in json.loads((ROOT / "docs/qa/postman/smart-cafe-local.postman_environment.example.json").read_text())["values"]}
        values.update({"baseUrl": base, "expiryBaseUrl": expiry_base, "fixturePassword": fixture_password,
                       "newPassword": new_password, "invalidToken": "invalid-token"})
        env_file.write_text(json.dumps({"values": [{"key": k, "value": v, "enabled": True} for k, v in values.items()]}), encoding="utf-8")
        folders = json.loads(COLLECTION.read_text(encoding="utf-8"))["item"]
        run_collection("01-login", folder=folders[0]["name"])
        write("UPDATE users SET auth_version=1 WHERE id=104; UPDATE users SET status='INACTIVE' WHERE id=110")
        run_collection("02-profile", folder=folders[1]["name"])
        before_other = sql("SELECT full_name,phone,auth_version,version FROM users WHERE id=102")
        run_collection("03-update", folder=folders[2]["name"])
        checked("name/address/phone persisted in users and staff_profiles", sql("SELECT CONCAT(full_name,'|',p.address,'|',phone,'|',version) FROM users JOIN staff_profiles p ON p.user_id=users.id WHERE users.id=103") == "Nguyễn Ánh staff|Đà Nẵng staff|+84 900 000 000|1")
        checked("missing personnel rolls back earlier users update", sql("SELECT CONCAT(full_name,'|',version) FROM users WHERE id=109") == "Synthetic Missing|0")
        before = snapshot()
        run_collection("04-security", folder=folders[3]["name"])
        checked("all rejected requests leave account/personnel rows unchanged", snapshot() == before)
        old_hash = sql("SELECT password_hash FROM users WHERE id=103")
        before_changed_at = sql("SELECT password_changed_at FROM users WHERE id=103")
        run_collection("05-password", folder=folders[4]["name"])
        changed_hash = sql("SELECT password_hash FROM users WHERE id=103")
        checked("new BCrypt hash differs and verifies new password", changed_hash != old_hash and bcrypt(new_password, changed_hash) == "MATCH")
        checked("old password no longer verifies", bcrypt(fixture_password, changed_hash) == "NO_MATCH")
        checked("password timestamp updated; auth version increments once; version preserves profile update", sql("SELECT password_changed_at FROM users WHERE id=103") != before_changed_at and sql("SELECT CONCAT(must_change_password,'|',auth_version,'|',version) FROM users WHERE id=103") == "0|1|2")
        checked("other ADMIN remains unchanged", sql("SELECT full_name,phone,auth_version,version FROM users WHERE id=102") == before_other)
        run_collection("06-expiration", folder=folders[5]["name"])
        checked("expired and flagged accounts clear persisted flag and increment auth version", sql("SELECT CONCAT(id,'|',must_change_password,'|',auth_version,'|',version) FROM users WHERE id IN(107,108) ORDER BY id") == "107|0|1|1\n108|0|1|1")
        run_collection("07-revocation", folder=folders[6]["name"])
        run_collection("08-regression", folder=folders[7]["name"])
        menu = ROOT / "docs/qa/postman/dt2-4-menu-api.postman_collection.json"
        run_collection("09-menu", source=menu)
        run_collection("10-qr", source=ROOT / "docs/qa/postman/dt2-4-qr-api.postman_collection.json")
        negative = json.loads(COLLECTION.read_text(encoding="utf-8"))
        negative["item"] = [negative["item"][7]["item"][0]]
        negative["item"][0]["event"][0]["script"]["exec"][0] = "pm.test('Negative control deliberately expects 201',()=>pm.response.to.have.status(201));"
        negative_file = OUT / "negative-control-collection.json"
        negative_file.write_text(json.dumps(negative), encoding="utf-8")
        run_collection("11-negative-control", source=negative_file, expect_failure=True)
        # Restore only our synthetic profile fixture and rerun every official request after the control.
        write("DELETE FROM staff_profiles; DELETE FROM users; DELETE FROM media_assets WHERE id IN(1001,1002)")
        write("SET @profile_fixture_hash='" + fixture_hash + "'; " + (ROOT / "docs/qa/postman/dt2-3-test-fixture.sql").read_text(encoding="utf-8"))
        for index, folder in enumerate(folders):
            run_collection("12-restored-" + str(index + 1), folder=folder["name"])
            if index == 0:
                write("UPDATE users SET auth_version=1 WHERE id=104; UPDATE users SET status='INACTIVE' WHERE id=110")
        checked("restored full run still persists new password", bcrypt(new_password, sql("SELECT password_hash FROM users WHERE id=103")) == "MATCH")
        # Bounded localhost security observation, separate from Newman acceptance counts.
        variables = {entry["key"]: entry["value"] for entry in json.loads(env_file.read_text())["values"]}
        def http(path, body=None, token=None):
            request = urllib.request.Request(base + path, data=json.dumps(body).encode("utf-8") if body is not None else None,
                                             headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})})
            begin = time.monotonic()
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    status = response.status
                    response.read()
            except urllib.error.HTTPError as error:
                status = error.code
                error.read()
            return {"status": status, "elapsedMs": round((time.monotonic() - begin) * 1000, 2)}
        before_security = snapshot()
        wrong_login = [http("/api/v1/auth/login", {"username": "pm_profile_admin2", "password": "WrongSynthetic1!"}) for _ in range(8)]
        wrong_current = {"currentPassword": "WrongSynthetic1!", "newPassword": new_password, "confirmNewPassword": new_password}
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            wrong_password = list(pool.map(lambda _: http("/api/v1/profiles/me/password", wrong_current, variables["admin2Token"]), range(8)))
        oversized = [http("/api/v1/auth/login", {"username": "pm_profile_admin2", "password": "a" * 131072}),
                     http("/api/v1/profiles/me/password", {**wrong_current, "currentPassword": "a" * 131072}, variables["admin2Token"])]
        valid = [http("/api/v1/auth/login", {"username": "pm_profile_admin2", "password": fixture_password}),
                 http("/api/v1/profiles/me", token=variables["admin2Token"])]
        require(all(item["status"] == 401 for item in wrong_login), "Unexpected login security observation")
        require(all(item["status"] == 400 for item in wrong_password + oversized), "Unexpected password/body observation")
        require(all(item["status"] == 200 for item in valid), "Observation caused account lockout")
        checked("bounded brute-force and oversized-body probes leave all account rows unchanged", snapshot() == before_security)
        result["securityAssessment"] = {"scope": "20 bounded localhost HTTP requests; observation, not a load/DoS certification",
                "wrongLogin": wrong_login, "wrongCurrentPassword": wrong_password, "passwordConcurrency": 4,
                "oversizedPasswordCharacters": 131072, "oversized": oversized, "validAfterFailures": valid,
                "rateLimitedResponses": sum(item["status"] == 429 for item in wrong_login + wrong_password),
                "bcryptCost": sql("SELECT SUBSTRING(password_hash,5,2) FROM users WHERE id=102"),
                "conclusion": "No throttle observed at these volumes; JSON field validation is not a transport body-size limit. Gateway/shared Auth policy still needs approval."}
        result["status"] = "PASS"
    except Exception as error:
        result["status"], result["error"] = "FAIL", str(error)
        raise
    finally:
        for child in children:
            child.terminate()
            try:
                child.wait(timeout=15)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=10)
        for log in logs:
            log.close()
        if created:
            sql(f"DROP DATABASE `{schema}`", False)
            result["ownedSchemaCleanup"] = "PASS"
        result["finishedAt"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        result["sourceSha256"] = {str(p.relative_to(ROOT)).replace(os.sep, "/"): hashlib.sha256(p.read_bytes()).hexdigest()
                                  for p in (ROOT / "backend/src").rglob("*") if p.is_file()}
        (OUT / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("SANITIZED_SUMMARY=" + str(OUT / "summary.json"), flush=True)


if __name__ == "__main__":
    main()
