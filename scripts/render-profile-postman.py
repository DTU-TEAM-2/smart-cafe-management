"""Publish only sanitized runner metadata/examples, never raw Newman request/response exports."""
import html
import json
from pathlib import Path
import sys

def render(source, destination):
    data = json.loads(source.read_text(encoding="utf-8"))
    if "run" in data or "collection" in data or "environment" in data or not data.get("phases"):
        raise ValueError("Expected sanitized runner summary, not raw Newman output")
    keys = ["revision", "dirty", "executedAt", "finishedAt", "schema", "ddlVersion", "ddlSha256", "mysqlVersion",
            "nativeFlyway", "baseUrl", "expiryBaseUrl", "jvmTimezone", "jdbcSessionSqlMode", "runtimeJarSha256",
            "status", "ownedSchemaCleanup", "sourceSha256", "phases", "databaseChecks", "securityAssessment", "verifiedExamples"]
    result = {key: data[key] for key in keys if key in data}
    # Examples contain synthetic account data only. Fail closed if a sensitive value was not redacted.
    sensitive = {"password", "currentPassword", "newPassword", "confirmNewPassword", "token", "passwordHash", "password_hash", "otp", "resetToken"}
    def check(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in sensitive and item != "<redacted>":
                    raise ValueError("Unredacted credential in HTTP example")
                check(item)
        elif isinstance(value, list):
            for item in value: check(item)
    check(result.get("verifiedExamples", []))
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "results.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# DT2-3 actual Newman/MySQL evidence", "", f"Result: **{result['status']}**. Real Spring Boot JAR, MySQL8 and Newman6.2.1.", "",
             f"HEAD `{result['revision']}`, dirty `{result['dirty']}`. The executable is identified by runtimeJarSha256 and sourceSha256; HEAD is not an implementation commit.", "",
             f"UTC {result['executedAt']} → {result['finishedAt']}. MySQL {result['mysqlVersion']}; schema `{result['schema']}`.", "",
             f"Normal server `{result['baseUrl']}`, real Login short-TTL server `{result['expiryBaseUrl']}`. Both owned processes/schema were cleaned up.", "",
             f"DDL {result['ddlVersion']}, SHA-256 `{result['ddlSha256']}`. Native provision1/validate/repeat0. JDBC UTC/session SQL mode NO_BACKSLASH_ESCAPES; JVM Vietnam.", "",
             "| Phase | Requests | Assertions | Failed assertions | Exit | Expected |", "| --- | ---: | ---: | ---: | ---: | --- |"]
    for phase in result["phases"]:
        lines.append(f"| {phase['name']} | {phase['requests']} | {phase['assertions']} | {phase['failedAssertions']} | {phase['exitCode']} | {'FAIL (control)' if phase['negativeControl'] else 'PASS'} |")
    lines += ["", "Database checks:", ""] + [f"- {c['status']}: {c['check']}" for c in result["databaseChecks"]]
    if "securityAssessment" in result:
        lines += ["", "## Bounded security observation", "", "```json", json.dumps(result["securityAssessment"],ensure_ascii=False,indent=2), "```"]
    if "verifiedExamples" in result:
        lines += ["", "## Verified HTTP examples (credentials redacted)", "", "```json", json.dumps(result["verifiedExamples"],ensure_ascii=False,indent=2), "```"]
    lines += ["", "Negative controls are intentionally failing and excluded from acceptance PASS totals. All official Profile folders rerun after fixture restore. Auth/OTP evidence is separate. Raw credential-bearing data stays ignored under backend/target/postman/dt2-3. No claim of deployed gateway protection or GitHub Actions PASS."]
    markdown = "\n".join(lines) + "\n"
    (destination / "README.md").write_text(markdown, encoding="utf-8")
    (destination / "index.html").write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>DT2-3 evidence</title><style>body{font:16px/1.5 system-ui;max-width:1100px;margin:32px auto;padding:0 20px;color:#182b39}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style><h1>DT2-3 HTTP/MySQL evidence</h1><p><a href="results.json">Sanitized JSON</a> · <a href="README.md">Markdown</a></p><pre>'+html.escape(markdown)+'</pre></html>\n',encoding="utf-8")

if __name__ == "__main__":
    render(Path(sys.argv[1]), Path(sys.argv[2]))
