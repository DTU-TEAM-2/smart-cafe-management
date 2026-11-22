## Vấn đề và kết quả

Task Jira và link nguồn chính thức:

- Mã Jira/DT2: `DT2-xx`
- Link Jira: `https://duymanhdnvn20092005.atlassian.net/browse/DT2-xx`
- Mã CF/QA dùng trong tiêu đề PR: `CF-xx` / `QA-xx`
- Nguồn xác nhận mã CF/QA: link Jira, backlog hoặc tài liệu handoff

Không tự đoán mã `CF/QA`. Nếu chưa có mã hoặc nguồn không khớp, để PR ở draft và hỏi owner.

Nguồn yêu cầu / quyết định liên quan:

Nhánh nguồn / tách từ / đích PR theo [Git Flow](../git-flow.md):

Hành vi trước và sau thay đổi:

## Kiểm tra và bằng chứng

- Lệnh / tình huống đã chạy và kết quả:
- Ảnh UI hoặc bằng chứng tích hợp nếu liên quan:
- Chưa kiểm tra và lý do:

## Tác động và bàn giao

- API / schema / cấu hình / dependency:
- Nếu đổi công nghệ/dependency: lý do, tác động và tính tương thích với Spring Boot / ReactJS / MySQL:
- Nếu liên quan dữ liệu: bảng/cột sử dụng, nguồn sơ đồ/DDL đối chiếu, chênh lệch và link quyết định chấp thuận nếu đổi schema:
- Rủi ro / migration / rollback nếu liên quan:
- Reviewer / phần cần tập trung:
- Skills đã đọc và áp dụng (identifier + SKILL.md path + ảnh hưởng):

## Trước khi merge

- [ ] Đạt AC và Definition of Done liên quan
- [ ] Checks phù hợp đạt; không có secret/dữ liệu cá nhân
- [ ] Đúng đích Git Flow; đã tự review/AI review và CI/bot checks đạt trước vòng duyệt
- [ ] Có ít nhất 2 reviewer khác tác giả approve revision hiện tại; đã xử lý blocker/discussion
- [ ] Nếu release/hotfix: có kế hoạch PR đồng bộ main → develop và cập nhật release đang mở nếu cần
- [ ] Tài liệu/hợp đồng liên quan đã cập nhật
- [ ] Tuân thủ [rule công nghệ](../.agents/rules/technology-constraints.md): Spring Boot / ReactJS / MySQL bắt buộc; công cụ khác tùy chọn trong phạm vi task
- [ ] Nếu liên quan dữ liệu: đã đối chiếu [rule database](../.agents/rules/database-source-of-truth.md); không tự chế bảng/cột/quan hệ hoặc sửa schema chưa được chấp thuận
