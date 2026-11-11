# Thiết lập GitHub

`main` là nhánh mặc định. GitHub tự xóa nhánh task sau khi PR được merge.

## Luật đang áp dụng cho main

- Mọi thay đổi đi qua PR. GitHub chặn force-push và xóa nhánh.
- Mọi thành viên có thể góp ý. GitHub yêu cầu xử lý các thảo luận trước khi merge. Approval là tùy chọn.
- Hai check bắt buộc là **Governance** và **PR title** từ GitHub Actions. Nhánh PR phải cập nhật theo `main`.
- Chỉ `NV-DuyManh` hoặc mentor `haitan2408` được merge vào `main` qua PR. Hai tài khoản đều phải đáp ứng các điều kiện CI và xử lý thảo luận.

Xem [luật PR và CI](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677767) và [luật giới hạn người merge](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677768).

CI hiện kiểm tra tài liệu và quy trình. Khi đưa ứng dụng vào repo, nhóm bổ sung build/lint/test theo phần code mới rồi cập nhật các check bắt buộc.
