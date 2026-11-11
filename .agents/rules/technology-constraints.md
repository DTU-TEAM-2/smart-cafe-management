# Ràng buộc công nghệ bắt buộc

Áp dụng cho mọi thành viên và AI agent, toàn bộ tính năng, đề xuất kiến trúc, dependency và môi trường kiểm thử của dự án.

## Nguồn và quyết định

Chủ repo yêu cầu ghi thành rule ngày **11/11/2026**, dựa trên [ảnh trao đổi của mentor Tân Hải](../../docs/handover/evidence/technology-constraints-2026-11-11.png): BE Spring Boot, FE ReactJS, database MySQL; phần còn lại là tùy chọn. Quyết định được ghi tại [ADR-004](../../docs/architecture/decisions.md).

| Thành phần | Bắt buộc |
| --- | --- |
| Backend ứng dụng | **Spring Boot** |
| Frontend ứng dụng | **ReactJS** |
| Database nghiệp vụ | **MySQL** |

Không tự thay backend bằng framework khác, frontend bằng nền tảng khác hoặc database nghiệp vụ bằng engine khác. Không thêm một backend/database song song để né stack đã chốt. Nếu có yêu cầu thay ba công nghệ này, phải có quyết định mới được chủ repo/người có thẩm quyền ghi nhận trước khi triển khai; skill hoặc đề xuất của AI không tự thay được quyết định.

## Công nghệ còn lại là tùy chọn

- Công cụ build, thư viện UI, truy cập dữ liệu, kiểm thử, migration, cache, realtime, lưu trữ ảnh và dịch vụ phụ trợ được chọn theo nhu cầu thực tế của task, tương thích với ba thành phần bắt buộc.
- Không ép toàn nhóm phải dùng một công cụ chỉ vì nó được nhắc trong skill hoặc có sẵn ở dự án cũ. Không tự thêm dịch vụ/dependency ngoài phạm vi task; khi có thay đổi cần nêu mục đích, tác động cấu hình và cách kiểm chứng trong PR theo quy trình hiện hành.
- **Tùy chọn không có nghĩa là thay thế stack chính.** Ví dụ công cụ quản trị DB không thay MySQL; dịch vụ ảnh không thay database nghiệp vụ; công cụ Node dùng build/test không thay backend Spring Boot.
- Vite, JDBC và các thư viện hiện có là lựa chọn triển khai đang dùng, không phải ràng buộc mới từ mentor. Giữ tương thích và version được khai báo trong manifest/lockfile; rule này không tự chốt hoặc nâng version Spring Boot, React, MySQL, Java hay Node.

## Thực hiện và review

1. Trước task, đối chiếu rule này với [rule database nguồn chuẩn](database-source-of-truth.md). MySQL bắt buộc không đồng nghĩa được tự chế schema.
2. Kiểm thử persistence theo hành vi MySQL phải chạy trên MySQL riêng đúng schema đã đối chiếu. Không dùng SQLite/H2/PostgreSQL hoặc mock để tuyên bố đã kiểm chứng tương đương MySQL; fixture UI được phép cho test giao diện nhưng phải ghi đúng phạm vi.
3. PR có thay đổi công nghệ/dependency phải nêu phần bắt buộc được giữ, lựa chọn tùy chọn và lý do, cấu hình/checks bị ảnh hưởng. Reviewer kiểm cả rule lẫn phạm vi task; CI xanh không chứng minh mọi quyết định công nghệ đều đã được chấp thuận.

Đây là quy tắc làm việc và review được neo tại `AGENTS.md`, tài liệu nhóm và checklist PR. Chưa có cơ chế CI tự động phát hiện mọi trường hợp thay stack; không tuyên bố có khóa kỹ thuật ngoài các kiểm tra hiện hành.
