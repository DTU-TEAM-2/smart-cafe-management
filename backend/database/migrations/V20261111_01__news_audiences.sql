-- Approved by the repository owner in chat on 2026-11-11 (ADR-005).
-- Manual, versioned schema extension. Application startup never executes this file.
-- Currently verified only against an isolated *_test database; system smart_cafe is unchanged.
-- Existing articles default to no audience and remain visible only to ADMIN until assigned.
ALTER TABLE news_posts
    ADD COLUMN audience_staff TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN audience_customer TINYINT(1) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_news_audience_flags
        CHECK (audience_staff IN (0, 1) AND audience_customer IN (0, 1));
