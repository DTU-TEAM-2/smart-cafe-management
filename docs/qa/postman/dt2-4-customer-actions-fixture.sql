-- Synthetic rows, isolated empty *_test only. Replace __TOKEN_HASH__ at runtime; never insert a raw credential.
INSERT INTO users(id,username,password_hash,full_name,email,role,status)
VALUES(95001,'actions-fixture-staff','test-only','Nhân viên test','actions-staff@example.invalid','STAFF','ACTIVE');
INSERT INTO cafe_tables(id,code,name,qr_public_id) VALUES(95001,'ACTIONS-T1','Bàn actions test',UUID());
INSERT INTO table_sessions(id,table_id,session_code,opened_by_user_id,started_at)
VALUES(95001,95001,'ACTIONS-SESSION-1',95001,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE));
INSERT INTO session_clients(id,session_id,access_token_hash,joined_at,expires_at)
VALUES(95001,95001,'__TOKEN_HASH__',UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR));
INSERT INTO menu_categories(id,code,name) VALUES(95001,'ACTIONS-CAT','Nhóm actions test');
INSERT INTO menu_items(id,category_id,code,name,price,available)
VALUES(95001,95001,'ACTIONS-ITEM','Cà phê test',25000,1),(95002,95001,'ACTIONS-OFF','Món ngừng phục vụ test',10000,0);
