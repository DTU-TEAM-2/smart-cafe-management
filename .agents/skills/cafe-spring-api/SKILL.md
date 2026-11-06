---
name: cafe-spring-api
description: Implement or debug Smart Cafe Spring Boot APIs, authorization and transactional cafe behavior. Use for backend code and API contracts; route schema-only work to cafe-database.
---

# Cafe Spring API

Inspect `backend/pom.xml`, existing security configuration and `docs/engineering/api-and-data.md`. Use docs for the installed Spring Boot version; do not copy old Spring Security adapters or assume Boot 3 package names work in Boot 4.

Keep backend authority over roles, table-session access, menu prices, order state and totals. Accept DTOs, validate inputs at the boundary and authorize the actual resource/session. Public routes must be explicitly allowlisted and tested. Do not add a temporary permit-all configuration.

For monetary behavior use `BigDecimal` with a documented VND rounding rule, server-loaded prices and historical name/price snapshots. Group state transitions and writes in a transaction. Plan uniqueness/idempotency and row/version locking where concurrent requests could duplicate orders or bills. Use an injectable `Clock` for business deadlines; do not trust client timers.

Separate requesting a bill, issuing it, receiving payment and closing a session. Follow recorded QA decisions for transitions. Avoid generic base controllers/services and exposing persistence entities as the API contract.

Run backend `verify`, relevant permission/error/transaction tests, and update the API contract. Hand off exact payloads, status codes and evidence to frontend/QA.
