# Menu quản trị — regression CSRF/session của TC-34

## Triệu chứng và phạm vi

[CI 42980658855](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42980658855/job/130390038025) có TC-34 trả 200/403 thay vì 200/409; test QR vẫn đạt. Không có bằng chứng endpoint tra QR gây lỗi này. Đây là lỗi cấu hình session của chain quản trị `/api/v1/menu/**` dùng chung.

## Tái hiện trên MySQL test riêng

Môi trường Windows, Java 25 (compile release 21), Spring Boot 4.1.2 / Spring Security 7.1.2, MySQL 8.0.46 trong container riêng; DB `smart_cafe_qr_verify_test`. Không truy cập DB hệ thống. Command dùng biến môi trường TEST_DB_URL/USERNAME/PASSWORD, không ghi credential/session cookie/token vào báo cáo:

```powershell
backend/mvnw.cmd -B -ntp -o -f backend/pom.xml '-Dtest=MenuHttpReportTest' '-Dmenu.http.race-repetitions=100' test
```

Trên Java 25 local có thêm `-DargLine=-javaagent:<đường-dẫn-mockito-core-5.23.0.jar>` để tránh self-attach bị treo; CI dùng JDK 21 theo repo.

- Trước sửa, vòng stress thất bại sau 18 cặp: DELETE tiếp theo bị 403; CsrfFilter ghi `Invalid CSRF token`. Không giảm kỳ vọng hoặc retry 403 thành PASS.
- Regression hẹp `MenuHttpReportTest#bearerReadsPreserveExistingCsrfSession` thất bại: sau Bearer GET, so sánh session ID trước/sau trả `false`. Test chỉ assert boolean để log không lộ giá trị cookie.
- Hai kiểm tra chứng minh session ID đang đổi ngay cả với Bearer polling; lỗi không phải stale-version 409 của database. Cookie cập nhật chồng nhau khi request song song có thể làm request sau dùng session ID cũ.

## Sửa cấu hình

`MenuSecurity` dùng `requireExplicitAuthenticationStrategy(true)`, `NullSecurityContextRepository`, `NullRequestCache`, cùng mô hình đã có ở chain chung. Session vẫn giữ CSRF; Bearer/role/status/auth_version được HeaderTokenFilter kiểm tra lại mỗi request. Không disable CSRF, không mở thêm role/route, không lưu authenticated identity vào session và không sửa schema/logic optimistic locking.

Đã bỏ cách cấu hình `STATELESS` cũ cho chain này: ở Spring Security 7.1.2, kết hợp nó với explicit authentication strategy bị framework từ chối khi khởi động. Thay vì tắt kiểm tra session fixation toàn hệ thống, chain Bearer không coi mỗi request là một login mới. Các chain/login khác không bị thay đổi bởi bản sửa này.

Tham chiếu [Spring Security 7.1.2 SessionManagementConfigurer](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.html): SessionAuthenticationStrategy mặc định dùng ChangeSessionIdAuthenticationStrategy; `requireExplicitAuthenticationStrategy` yêu cầu gọi strategy tường minh.

## Regression sau sửa

- Regression cookie: ba Bearer GET liên tiếp giữ session ID; cùng cookie nhưng không có Bearer vẫn bị **401**.
- Giữ nguyên TC-34 assertion chính xác `200 + 409`, hậu kiểm version bằng 1. Mặc định suite chạy **20 cặp** thay cho một cặp; có tham số test-only để stress 100 cặp.
- Lượt stress local sau sửa: **2/2 test trong MenuHttpReportTest PASS**, gồm cookie regression và toàn bộ HTTP report. **100/100 cặp PUT** đều có đúng một 200, một 409; **0 ca HTTP FAIL**, kể cả thiếu/sai CSRF, STAFF không đủ quyền và các ca CRUD còn lại.
- Không coi một lần CI xanh là đủ để bỏ qua finding. Sau commit cần chạy full Maven MySQL, CI và Newman lại trên base cập nhật của #9/#10, ghi đúng revision/kết quả tại PR.
- Không xóa hoặc tự resolve discussion của reviewer. PR #10 vẫn Draft: yêu cầu phiên khách vãng lai và dependency #9 chưa được nghiệm thu.

Skills: `diagnosing-bugs`, `cafe-spring-api`, `cafe-quality` cho tái hiện/sửa/kiểm chứng; `cafe-review` cho handoff. Không để instrumentation log tạm hoặc giá trị credential trong source.
