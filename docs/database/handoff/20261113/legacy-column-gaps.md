# Column gaps before legacy reconciliation

Compared the PR #7 DDL-only snapshot with the 20-table local reference. This table lists column presence, not type/default/index/constraint changes.

| Table | Present only in local reference | Present only in legacy snapshot |
| --- | --- | --- |
| auth_otp_challenges | — | delivery_attempts, last_smtp_status, next_delivery_at, otp_ciphertext |
| auth_otp_limits | — | — |
| bill_items | item_code, line_total, order_item_id, session_id | menu_item_id, subtotal |
| bills | created_by_user_id, creator_name_snapshot, current_session_id, request_hash, table_code_snapshot, version, void_reason, voided_at, voided_by_user_id | creator_name, table_id, table_number_snapshot |
| cafe_tables | code, qr_public_id, updated_at, version | — |
| menu_items | available, category_id, image_media_id, updated_at, version | image_url |
| service_requests | acknowledged_by_user_id, active_request_key, idempotency_key, requested_by_client_id, resolved_at, resolved_by_user_id | table_id |
| table_sessions | closed_by_user_id, join_code_hash, opened_by_user_id, version | — |
| users | — | — |
