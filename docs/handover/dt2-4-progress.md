# DT2-4 — Bảng theo dõi tiến độ Backend

Cập nhật ngày 13/11/2026 theo trạng thái repository và PR đã kiểm tra. Đây là tiến độ triển khai và kiểm chứng, không phải xác nhận Done hoặc đã merge. Phạm vi hiện tại chỉ backend; không triển khai frontend hoặc Mini Game.

| Chức năng | Ảnh đã đối chiếu trong ZIP | Tiến độ hiện tại | PR / phạm vi |
| --- | --- | --- | --- |
| 1. Xem Menu | Menu/danh mục, chi tiết món, đang tải thực đơn | Đã merge vào main qua `8476ee3`; đã sửa P2 tìm kiếm theo SQL mode và hồi quy CSRF TC-34. Không đồng nghĩa hoàn thành toàn bộ DT2-4. | [#9](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/9) — API danh mục, danh sách/lọc/tìm kiếm/phân trang và chi tiết món |
| 2. Bàn và phiên khách hàng | ZIP không có ảnh QR/tham gia phiên riêng | **Đã có phần tra QR → đúng bàn, đang review độc lập. Chưa hoàn tất phần tự mở/xác thực phiên**, đang vướng người mở phiên trong schema và QA-02. | [#10](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/10) — nhắm develop, PR riêng cho API tra QR |
| 3. Gọi món | [Giỏ hàng, trạng thái tải, gửi thành công/thất bại](evidence/dt2-4/reference/03-cart.png) | API lấy giá từ MySQL, lưu snapshot và chống gửi trùng. Cần token phiên đã cấp. | `feature/dt2-4_customer-workflows` |
| 4. Sửa/Remove món | [Giỏ hàng và chi tiết món, ZIP không có ảnh sửa sau đặt riêng](evidence/dt2-4/reference/02-item-detail.png) | Backend kiểm 3 phút, trạng thái SUBMITTED, người gửi và phiên bản. | `feature/dt2-4_customer-workflows` |
| 5. Gọi nhân viên | [Yêu cầu phục vụ, gửi thành công/thất bại](evidence/dt2-4/reference/12-waiter-success.png) | Ghi WAITER và dùng Header/Sales hiện có. | `feature/dt2-4_customer-workflows` |
| 6. Yêu cầu thanh toán | [Yêu cầu thanh toán thành công/thất bại](evidence/dt2-4/reference/14-bill-request-success.png) | Ghi BILL cho đúng phiên. Việc lập và thu tiền vẫn thuộc DT2-6. | `feature/dt2-4_customer-workflows` |
| 7. Feedback | [Đánh giá trải nghiệm](evidence/dt2-4/reference/05-feedback.png) | Gửi tên, email, nội dung và rating, lưu phản hồi UNREAD. | `feature/dt2-4_customer-workflows` |
| 8. Upload ảnh Feedback | [Vùng đính kèm ảnh tùy chọn](evidence/dt2-4/reference/05-feedback.png) | Đã chuẩn bị adapter và metadata. Chờ bucket và quyền để chạy Firebase thật. | `feature/dt2-4_customer-workflows` |

## Bằng chứng đã có

- [Bàn giao các chức năng 3–8 và ảnh kiểm thử](dt2-4-remaining-backend.md). MySQL **89/89**, Newman **17/17** request nghiệp vụ khách và **16/16** request Feedback. Upload Firebase thật đang chờ cấu hình.

- Tích hợp: #9 đã merge tại [8476ee3](https://github.com/DTU-TEAM-2/smart-cafe-management/commit/8476ee30536fdd01b521cb53e5bb22d38b3b5833). Nhánh #10 đồng bộ commit này qua `9790cbb`; tree sau merge trùng `a342090`, không thay đổi mã ứng dụng. Theo ảnh quy trình chủ repo cung cấp trong chat ngày 13/11/2026, task PR nhắm develop, cần CI xanh và hai người khác tác giả approve; maintainer quyết định merge. Main/develop cùng ở `8476ee3` khi đối chiếu; #10 review riêng phần tra QR, phần phiên vẫn chờ quyết định.
- Sau sửa CSRF TC-34 tại `a342090`: CI MySQL **66/66**, 0 lỗi/skip; [CI](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42983165051) và [Postman Menu + QR](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42982814435) thành công. Các lượt cũ dưới đây giữ để truy xuất, không cộng dồn số test.
- Menu: Maven MySQL **60/60** tại revision `495e9db`, [CI sau sửa P2](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42976528798); Newman strict SQL mode **16/16 request, 31/31 assertion**, [lần chạy](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42976528804).
- QR: Maven local MySQL **65/65**, test MySQL QR bổ sung **1/1** (không cộng vào 65); collection QR qua Newman **9/9 request, 16/16 assertion**. [CI branch QR](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42979865804) và [workflow Postman Menu + QR](https://github.com/DTU-TEAM-2/smart-cafe-management/actions/runs/42979865948) đều thành công tại `4d4ab00`.
- Ảnh kiểm thử và JSON: [Menu](../reports/dt2-4-postman/README.md), [QR](https://github.com/DTU-TEAM-2/smart-cafe-management/blob/77ddb61/docs/reports/dt2-4-qr/README.md). Các ảnh báo cáo collection chạy bằng Newman không phải ảnh Postman Desktop.
- [Bộ 15 ảnh tham chiếu đã phân loại theo chức năng](evidence/dt2-4/reference/README.md); giữ nguyên ảnh người dùng cung cấp. Ảnh thiết kế không phải bằng chứng backend đã hoàn thành.

## Phần cần quyết định để tiếp tục phiên

[Chi tiết blocker chức năng 2](https://github.com/DTU-TEAM-2/smart-cafe-management/blob/77ddb61/docs/handover/dt2-4-table-session.md): khách quét QR muốn tự mở phiên nhưng `table_sessions.opened_by_user_id` hiện NOT NULL FK tới `users`, chưa có cách lưu người mở là khách vãng lai được phê duyệt. Không giả tài khoản, gán ADMIN thay khách hoặc tự sửa schema. Cơ chế nhiều thiết bị, token và CHECKOUT còn cần QA-02.

## Cách cập nhật

Theo yêu cầu ngày 13/11/2026, các chức năng DT2-4 còn lại dùng chung nhánh feature/dt2-4_customer-workflows. Khi có kết quả mới, cập nhật đúng dòng cùng link PR và bằng chứng thực tế; tách rõ đã code, đã kiểm thử, đang review, bị chặn và đã merge. Không đánh dấu 7 chức năng còn lại hoàn tất từ kết quả test của Menu/QR.

Nguồn: [Jira DT2-4](https://duymanhdnvn20092005.atlassian.net/browse/DT2-4), [DT2-33](https://duymanhdnvn20092005.atlassian.net/browse/DT2-33), [câu hỏi mở](../product/open-questions.md), yêu cầu và ZIP người dùng cung cấp trong chat. Skills kiểm tra tài liệu: `cafe-quality` tại `.agents/skills/cafe-quality/SKILL.md`.
