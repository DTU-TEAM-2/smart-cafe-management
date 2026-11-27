# DT2-3 — Postman/Newman và MySQL thật

Contract [OpenAPI](../api/dt2-3-profile.yaml), [collection](postman/dt2-3-personal-account.postman_collection.json), [environment mẫu không credentials](postman/dt2-3-personal-account.postman_environment.example.json), [fixture nghiệp vụ](postman/dt2-3-test-fixture.sql), [kết quả hiện hành](../reports/dt2-3-postman/results.json).

## Chạy lại

Dùng Java21, MySQL8, Python3 và Newman6.2.1. Cấp TEST_DB_URL loopback trỏ schema kết thúc `_test`, TEST_DB_USERNAME/PASSWORD bằng cấu hình riêng ngoài Git; MYSQL_BIN là mysql client, NEWMAN_CLI là đường dẫn `newman/bin/newman.js`. Không đưa mật khẩu lên command line hay file environment versioned. Tài khoản test cần quyền tạo/xóa schema mình sở hữu; không chạy bằng cấu hình DB chung.

```powershell
# Sau khi nạp các biến môi trường riêng một cách an toàn:
./test-be.ps1
./backend/mvnw.cmd -f backend/pom.xml -B -ntp dependency:build-classpath '-Dmdep.outputFile=target/dt2-3-classpath.txt'
python scripts/profile-postman-live.py
python scripts/render-profile-postman.py backend/target/postman/dt2-3/summary.json docs/reports/dt2-3-postman
```

Full verify cần TEST_DB_URL, nếu không các MySQL tests có thể bị skip: không dùng lượt đó làm bằng chứng. Runner Profile tự sinh một schema `smart_cafe_dt2_3_<random>_test`, provision native migration20261113.1, validate/repeat, seed dữ liệu tổng hợp, chạy JAR thật trên cổng loopback rảnh và server Login TTL ngắn riêng. SQL-mode được đặt qua JDBC sessionVariables trước khi Connector/J cache cấu hình; JVM Vietnam/JDBC UTC. Runner đối chiếu class trong JAR với build classes và ghi source manifest/hash. Không thay collection Menu/QR để hợp thức hóa regression.

| Nhóm | Các điều kiện chính |
| --- | --- |
| Login | ADMIN/STAFF/CUSTOMER, status/password fields ở top-level, token thật; wrong credentials |
| View | Own identity, nullable personnel, deleted avatar, ID/salary precision, Header equality |
| Update | ADMIN và STAFF, omitted fields, Unicode giới hạn, address missing rollback, version hợp lệ/stale |
| Permission | CUSTOMER403; thiếu/tampered/expired/revoked bearer401; gửi role/salary/status/userId khác400, DB không đổi |
| Password | current sai, reuse, mismatch, PasswordPolicy/72-byte, success, token replacement, no blind retry |
| Expiration | Ngay trước/đúng/sau30×24h, DB flag, Login/Profile thống nhất; boundary chính xác dùng Clock test |
| Revocation | Password/reset/auth_version/role/status hiện tại; token cũ bị từ chối |
| Regression | Header, health, toàn collection Menu và QR nguyên trạng |

Tám folder Profile có89 request/270 assertion. Runner chạy đủ một vòng, kiểm snapshot DB, restore fixture rồi **chạy lại cả tám folder**; không chỉ chạy lại ca lỗi. Menu16/31 và QR9/16. Tổng203 request/587 assertion acceptance; health negative control1 request/3assertion có1fail và CLI exit1 được tách riêng. 10 kiểm SQL bao gồm fail-no-write, password hash thay/reset flag/tăng versions, rollback, cleanup và security-probe không đổi row. Concurrency thực sự nằm trong MySQL suite/test rehearsal; Newman chạy tuần tự không được coi là concurrency.

Auth/OTP regression riêng: provision native verified DDL trước trên schema `_test` trống do mình sở hữu theo [DatabaseRehearsal handoff](../handover/dt2-2-verified-database-integration.md). Nạp TEST_DB_* cùng schema đó, MYSQL_BIN, NEWMAN_MODULE và BCRYPT_TEST_CLASSPATH (target/classes + dependency classpath) từ private launcher; đặt **AUTH_TEST_PROFILE=verified-db** rồi chạy `node scripts/auth-postman-live.cjs` (profile lấy từ biến môi trường, không phải positional argument). Backend HTTP và SMTP capture loopback thật:39 request/57assertion acceptance,4negative-control assertion cố ýfail; [JSON sanitized](../reports/dt2-3-postman/auth-regression.json). Runner dọn fixtures/process của mình; wrapper tạo schema phải xóa schema đó trong finally. Không phải Gmail production.

Probe bảo mật mới chỉ20 request localhost có giới hạn:8 Login sai tuần tự;8 current-password sai tối đa4 đồng thời;2 field131072 ký tự; Login/Profile hợp lệ sau lỗi. Ghi latency/status, không benchmark CPU/DoS và không chứng nhận throttle. Xem [đánh giá và đề xuất](../reports/dt2-3-hardening.md).

Raw Newman exports, runtime environment, bearer/password và server logs ở backend/target/postman/dt2-3 **ignored/private**, không đưa Git. Renderer chỉ nhận summary sanitized, từ chối raw report và ví dụ chưa che credential. Báo cáo versioned chỉ giữ dữ liệu tổng hợp, hash, số liệu và response mẫu đã redacted. Khi chạy lại, render lại đúng summary mới nhất; không dùng báo cáo cũ làm bằng chứng cho source khác.

Generated README/HTML của renderer là local review aids, không thuộc stage allowlist cuối. Evidence chọn commit là results.json/Auth JSON đã sanitized và báo cáo hardening; không stage cả output directory khi render lại.
