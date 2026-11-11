# Smart Cafe — khởi động lại quy trình

Repo mới bắt đầu từ nhánh `main` rỗng. Bộ quy tắc và skill được đề xuất bằng PR riêng; chỉ có hiệu lực trong repo sau khi PR được review và merge.

## Phạm vi PR nền tảng

- Giữ 17 skill, registry, bộ định tuyến và luồng chuyển phase.
- Giữ quy tắc làm việc nhóm, đặt tên, Git/PR, chất lượng và bàn giao.
- Không nhập code ứng dụng, migration, dữ liệu, credential, kết quả test ứng dụng hoặc lịch sử PR từ repo cũ.
- Giữ attribution, license và manifest của skill kế thừa; không tự nhận nội dung bên thứ ba là sản phẩm mới.

## Bắt đầu lại từ đâu

1. Review và chốt bộ quy tắc/skill trong PR nền tảng.
2. Thu thập SRS/project plan hiện hành vào `docs/handover/`; xác nhận phạm vi và các câu hỏi nghiệp vụ.
3. Tạo backlog với owner, tiêu chí nghiệm thu, phụ thuộc và reviewer; không chuyển task cũ sang Done tự động.
4. Chốt mô hình dữ liệu, quyền, hợp đồng API và quyết định kiến trúc trước khi triển khai phần phụ thuộc.
5. Làm từng task trên nhánh ngắn hạn từ `main`, kiểm chứng theo rủi ro, review rồi merge qua PR.

Kiểm tra nền tảng:

```sh
node scripts/skills.mjs check
node --test scripts/skills.test.mjs
```

## Backend DT2-9 — quản lý món và nhóm món

Nhánh `feature/dt2-9-manage-menu-items-and-categories-backend` bổ sung backend Spring Boot/JDK 21/MySQL, viết lại từ nguồn tham chiếu legacy. Không nhập dữ liệu/migration history hay tài khoản cũ. FE nằm trên nhánh frontend riêng, chưa tích hợp API trong nhánh này.

Hướng dẫn cấu hình, chạy và kiểm thử: [backend/README.md](backend/README.md). Hợp đồng: [OpenAPI](docs/api/dt2-9-menu.yaml). Nguồn: [yêu cầu backend](docs/handover/dt2-9-backend-request.md).

Hướng dẫn quy trình: [AGENTS.md](AGENTS.md), [đóng góp](CONTRIBUTING.md), [skill](SKILLS_INSTALLED.md).
