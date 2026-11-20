-- Synthetic data ONLY. Caller must verify a dedicated, fresh *_test schema.
-- Password values cannot authenticate; the runner uses the shared signed-token contract with an ephemeral test secret.
INSERT INTO users(id,username,password_hash,full_name,email,role,status) VALUES
(96001,'feedback-http-admin','not-a-login-hash','Quản lý HTTP test','admin-http@example.invalid','ADMIN','ACTIVE'),
(96002,'feedback-http-staff','not-a-login-hash','Nhân viên HTTP test','staff-http@example.invalid','STAFF','ACTIVE');
INSERT INTO staff_profiles(user_id,address,salary) VALUES(96001,'Địa chỉ test',10000000),(96002,'Địa chỉ test',8000000);
INSERT INTO feedback(id,author_name,author_email,content,rating,created_at) VALUES
(96001,'Khách kiểm thử HTTP','customer-http@example.invalid','Cà phê ngon, cảm ơn quán!',5,'2026-11-18 17:00:00'),
(96002,'Khách ngày tiếp theo','next-http@example.invalid','Phản hồi khác ngày.',4,'2026-11-19 17:00:00');
