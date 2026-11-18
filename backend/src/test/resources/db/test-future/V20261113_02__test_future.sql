-- Test-only version, not allocated to any deployment.
ALTER TABLE news_posts ADD COLUMN integration_probe int NOT NULL DEFAULT 0;
