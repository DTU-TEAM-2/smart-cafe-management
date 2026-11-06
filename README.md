# Smart Cafe — khởi động lại quy trình

Nhánh `main` chứa bộ quy tắc và skill phát triển Smart Cafe. Nền tảng được tích hợp qua [PR #1](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/1).

## Phạm vi PR nền tảng

- Giữ 17 skill, registry, bộ định tuyến và luồng chuyển phase.
- Giữ quy tắc làm việc nhóm, đặt tên, Git/PR, chất lượng và bàn giao.
- Không nhập code ứng dụng, migration, dữ liệu, credential, kết quả test ứng dụng hoặc lịch sử PR từ repo cũ.
- Giữ attribution, license và manifest của skill kế thừa; không tự nhận nội dung bên thứ ba là sản phẩm mới.

## Bắt đầu lại từ đâu

1. Đọc bộ quy tắc và chọn skill phù hợp với task.
2. Thu thập SRS/project plan hiện hành vào `docs/handover/`; xác nhận phạm vi và các câu hỏi nghiệp vụ.
3. Tạo backlog với owner, tiêu chí nghiệm thu, phụ thuộc và reviewer; không chuyển task cũ sang Done tự động.
4. Chốt mô hình dữ liệu, quyền, hợp đồng API và quyết định kiến trúc trước khi triển khai phần phụ thuộc.
5. Làm từng task trên nhánh ngắn hạn từ `main`, kiểm chứng theo rủi ro, review rồi merge qua PR.

Kiểm tra nền tảng:

```sh
node scripts/skills.mjs check
node --test scripts/skills.test.mjs
```

Repo hiện chưa có backend/frontend nên chưa có lệnh chạy ứng dụng hoặc kiểm chứng database. Hướng dẫn chi tiết: [AGENTS.md](AGENTS.md), [quy trình](CONTRIBUTING.md), [skill](SKILLS_INSTALLED.md).
