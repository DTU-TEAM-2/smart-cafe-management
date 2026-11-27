# DT2-3 / DT2-27 — yêu cầu đã đối chiếu ngày 15/11/2026

> Cập nhật18/11/2026: owner xác nhận **CF-04** và cho phép commit/push/Draft PR với title chính xác bên dưới. Các ghi nhận CF chưa biết/không publication ngày15/11 là lịch sử, được thay bởi authorization này; business scope/QA #8 không đổi. [Publication verification](../reports/dt2-3-publication.md).

Tài liệu này ghi phạm vi nguồn đã đọc, không sửa SRS/Jira gốc và không đánh dấu Jira Done.

| Nguồn đã truy cập | Phần áp dụng |
| --- | --- |
| [SRS v4.1](https://docs.google.com/document/d/1Mhaq4ttf8d-uiPbv2gSnfHK0AhCu4qpc/edit), tệp DOCX sửa cuối 04/11/2026 | 6.1.1 login; 6.1.2 hồ sơ từ avatar, các trường Username/Name/Address/Tel/Role/Birthday/Gender/Salary; 6.1.3 đổi mật khẩu và trở lại hồ sơ; 6.1.4–6.1.6 validation nhân viên; 6.3.1 Header. Nội dung chữ được đọc qua connector; không tuyên bố đã render toàn bộ hình ảnh nhúng của SRS. |
| [Project Plan](https://docs.google.com/spreadsheets/d/1nT1hxrsgVdBxTbORlTwohwzmbvujldYR/edit), sửa cuối 11/11/2026 | Tasks_Sprint_1, STT 03: quá 30 ngày yêu cầu thay mật khẩu; Update chuyển trường được sửa thành input. Không coi Schedule lịch sử 2021 là lịch nghiệm thu. |
| [SRS Q&A](https://docs.google.com/spreadsheets/d/1m2vnTOdGWivwqokRiJYOpWmY2u3G_o-U/edit), sửa cuối 14/11/2026 | QA #8: STAFF tự sửa tên/địa chỉ/SĐT; quyền/lương do ADMIN quản lý; Answer “Ok em”, Answered. Connector trả không có comment/reply; ô Answer không ghi danh tính người trả lời. |
| [DT2-3](https://duymanhdnvn20092005.atlassian.net/browse/DT2-3), [DT2-27](https://duymanhdnvn20092005.atlassian.net/browse/DT2-27) và DT2-25/26/28/29/30 | Phạm vi cá nhân, password/30 ngày, dependency Header/Staff. Bản Jira vẫn ghi mặc định Admin-only nếu chưa xác nhận QA. Đã đọc description/status/comment/links được trả về; các issue này không có comment. |
| DT2-1/2/10/69; [PR #7](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/7), [#11](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/11), [#14](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/14), [#15](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/15) | Auth đã merge develop; ba PR còn mở. Đọc metadata/discussion, kiểm writer Staff và các file security/contract liên quan ở remote refs; không nhập commit các PR này vào task. Finding lịch sử không được coi là lỗi hiện tại nếu chưa tái hiện. |

Truy cập Drive thành công bằng kết nối “khale”; hai kết nối còn lại trả 404. Không coi 404 đó là bằng chứng tài liệu không tồn tại. `PHAN_TICH_DU_AN_CAFE.md` và backlog chứa mapping CF không có trong checkout; thư mục Drive Study_Requirement/Sprint_1 trả danh sách rỗng. Không đoán mã CF.

## Quyết định trực tiếp của owner trong chat ngày 15/11/2026

1. Áp dụng QA #8: STAFF sửa đúng `name`, `address`, `phone` của mình. ADMIN được sửa các trường cá nhân trong phạm vi đã duyệt; endpoint này chỉ nhận cùng ba trường. Không sửa username/role/salary/status/auth/email/birthday/gender/avatar. Quản trị nhân viên thuộc DT2-10. CUSTOMER bị từ chối toàn bộ ba API hồ sơ nhân sự.
2. Hết hạn tại đúng 30 × 24 giờ UTC. `mustChangePassword` là cờ DB OR đã đến hạn; Login và Profile đều trả trạng thái này. Chưa khóa API nghiệp vụ. Từ chối mật khẩu mới trùng mật khẩu hiện tại bằng BCrypt matches.
3. Sau đổi password, commit thay hash/timestamp/flag/auth_version/version rồi cấp bearer mới bằng TokenService; token cũ và reset grant cũ không còn hợp lệ. UI thay bearer rồi mở lại hồ sơ.
4. Chưa biết CF: dùng DT2-3/DT2-27 và nhánh hiện tại; thiếu CF chỉ chặn commit/title PR cuối cùng. Không sửa CI/validator. Chưa push/tạo PR và không hỏi lại CF trong quá trình triển khai.

Quyết định này giải quyết khác biệt SRS v4.1/Jira Admin-only bằng yêu cầu bổ sung QA #8 được owner yêu cầu áp dụng; không suy diễn thêm quyền. Xem [ADR-013](../architecture/decisions.md#adr-013--hồ-sơ-cá-nhân-và-vòng-đời-mật-khẩu-dt2-3-15112026).

## Tiêu chí nghiệm thu

- AC1: ADMIN/STAFF ACTIVE chỉ đọc đúng tài khoản từ principal và auth_version hiện hành; thiếu nhân sự trả null, không bịa lương/địa chỉ; avatar đã xóa không được trả. ID/lương/version của Profile là chuỗi thập phân.
- AC2: PATCH allowlist ba trường + expected version; trường bị bỏ qua giữ nguyên, null/whitespace/khác kiểu/quá độ dài bị từ chối. Unknown/protected field bị từ chối toàn request. Chuỗi tiếng Việt và dấu nháy được giữ như dữ liệu.
- AC3: Ghi users + staff_profiles trong một transaction, stale version trả 409, thiếu staff_profiles khi sửa địa chỉ trả 409 và rollback users; hai editor cùng version chỉ một thành công. Không tự tạo hồ sơ thiếu lương.
- AC4: Đổi password yêu cầu current/new/confirmation, PasswordPolicy hiện hữu, không trim, không plaintext; lỗi không thay dữ liệu. Hai thay đổi cùng credential và race với reset chỉ một thành công. Không phát token khi commit thất bại.
- AC5: Login/Profile thống nhất ngay trước/đúng/sau 30 ngày và cờ DB, dưới UTC và JVM Vietnam; người quá hạn vẫn đổi được password rồi tiếp tục bằng bearer mới.
- AC6: Regression Auth/OTP/Header/Menu/QR/Sales, MySQL native verified migration, frontend hiện hữu, Newman thật và negative control; báo đúng PASS/FAIL/NOT RUN. Staff/News/Customer Workflows chưa merge chỉ có kiểm source/contract, không gọi đó là test tổ hợp đã tích hợp.

Bổ sung verification15/11/2026 theo prompt hardening tiếp theo: đã chạy rehearsal **chọn lọc, chưa được duyệt** trong managed worktree với HEAD mới Staff/Customer/News. Không còn chỉ source review; kết quả và giới hạn tại [hardening](../reports/dt2-3-hardening.md). Candidate396test/2failure/0error/0skip, không phải tích hợp lên develop hoặc phê duyệt PR. Phần cuối AC6 về trạng thái kiểm chứng trước hardening là lịch sử; các quyền và quyết định nghiệp vụ phía trên không đổi.

## CF và publication — owner xác nhận18/11/2026

Mapping chính thức: **CF-04 → DT2-3 / DT2-27**. Commit và Draft PR dùng đúng:

```text
feat(account): CF-04 implement DT2-3 account profile and password management backend
```

Giữ feature/dt2-3_personal-account-backend, target develop, reviewer dự kiến NV-DuyManh/haitan2408. User cho phép commit/push/Draft và sửa lỗi CI thuộc phạm vi; không force-push, merge/self-approve/JiraDone hoặc sửa policy để vượt checks. Quyết định này không phê duyệt shared rate policy, schema/deployment hoặc hợp đồng Staff proposal.
