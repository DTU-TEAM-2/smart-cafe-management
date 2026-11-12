# Auth collection — isolated test use only

Use `Smart_Cafe_DT2-2_Auth.postman_collection.json` only with synthetic accounts in a dedicated MySQL `*_test` schema. The environment template contains no credentials/tokens/OTP and defaults to a test-only loopback URL, not the ordinary application port. Never point the recovery flow at the team's Admin/Staff accounts. Do not export raw Newman JSON/HTML, request bodies, environments or console logs containing tokens/passwords/OTP.

Original requests 1–23 remain ordered. Recovery request 13 now targets `staffEmail`, matching the Staff account checked at step 22. Provide `newStaffPassword` and a `wrongOtpCode` different from the captured OTP. Steps 19–22 unconditionally assert success; step 19 clears stale resetToken first, step 20 requires valid=true/no-store, and step 22 requires a nonempty bearer for exactly staffUsername. Step 23 acknowledges **stateless** logout; it does not assert server revocation. The final folder adds expired/used/replaced/locked OTP, invalid/expired/used grant, mismatch/weak password, old password/bearer and removed GET endpoint cases. It needs the isolated fixture variables described by the live runner.

## Assertion integrity

Install/use Newman 6.2.3 (the verified tool version) as an external test tool (outside this repository's application dependencies). Set `NEWMAN_MODULE` to its package directory if not resolvable as `newman`, then:

```powershell
node --test scripts/auth-postman.test.cjs
```

This uses a local HTTP stub only to check that collection assertions reject bad results. It is **not** backend/MySQL/recovery evidence. Wrong HTTP status, valid=false, empty tokens and a different account must fail; valid examples must pass.

## Real HTTP + MySQL + loopback SMTP

First provision an approved isolated schema and run `test-be.ps1` to build the current JAR. Supply only `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`; no production DB fallback exists in this runner. URL must be MySQL, loopback and a `*_test` schema. Set `MYSQL_BIN` to the MySQL CLI and `BCRYPT_TEST_CLASSPATH` to the installed Spring Security Crypto and Commons Logging JARs (the same cached dependencies used by the project), separated by the platform classpath delimiter. For example on Windows:

```powershell
$env:MYSQL_BIN = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
# TEST_DB_* are private environment values, never commit/export them.
# BCRYPT_TEST_CLASSPATH = <spring-security-crypto-7.1.2.jar>;<commons-logging-1.3.6.jar>
node scripts/auth-postman-live.cjs
```

`AUTH_TEST_PROFILE=dev` or `prod` runs the real corresponding runtime profile with explicit isolated datasource, stable temporary key, loopback mail overrides and Flyway disabled. Default is `auth-http-test`. This checks the profile on disposable fixtures; it does not approve shared database startup or production credentials.

The opt-in runner:

- Verifies SELECT DATABASE() before fixture writes, creates two unique synthetic users with generated passwords/BCrypt and a private temporary signing key. It never alters schema or starts Flyway/SQL init.
- Starts its own JAR on a random loopback HTTP port and a real loopback SMTP capture socket. No Gmail/external recipient or application test endpoint is used. The test-tool Java helper reads passwords from stdin, not process arguments.
- Runs original 1–15, injects the captured OTP into testOtpCode in memory, then runs 16–23 in their original order. Password reset and new-password login operate on the same Staff fixture. Actual HTTP responses are checked by Newman.
- Prepares expired/rotated/locked challenges only for its own fixture using actual API calls plus isolated fixture clock/state updates. It then runs the separate negative folder.
- Deliberately supplies a wrong OTP, invalid reset grant and wrong new-password login: these Newman runs must report **FAIL**, not success. The surrounding harness checks that the expected failures occurred and records their original verdict.
- Observes existing login/dummy controls without changing policy. The absence of a new throttle/cleanup implementation is intentional pending owner decisions, not a production protection claim.
- Stops only its own backend/SMTP, removes only its generated fixture emails/users/HMAC limit keys, and verifies fixture-user cleanup. No real account data is copied. All secrets remain in process memory/private environment; stdout is restricted to scenario names and counts, never raw responses or environments.

Expect original 23 requests plus the negative folder to pass; fault-injection scenarios must show their expected FAIL verdicts. If any prerequisite, fixture or scenario fails, the process exits nonzero. A fixture/setup failure is not a security regression PASS. Do not use skipped/missing Newman runs as acceptance evidence.
