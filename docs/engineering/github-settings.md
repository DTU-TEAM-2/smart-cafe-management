# Thiết lập GitHub

`main` là nhánh mặc định. GitHub tự xóa nhánh task sau khi PR được merge.

## Luật đang áp dụng cho main

- Mọi thay đổi đi qua PR. GitHub chặn force-push và xóa nhánh.
- PR cần ít nhất một approval và approval từ code owner của phần thay đổi. Mentor `haitan2408` có quyền Write và là code owner mặc định. Push commit mới làm approval cũ hết hiệu lực. Các thảo luận phải được xử lý.
- Hai check bắt buộc là **Governance** và **PR title** từ GitHub Actions. Nhánh PR phải cập nhật theo `main`.
- Chỉ chủ repo `NV-DuyManh` được cập nhật `main` qua PR. Quyền ngoại lệ này chỉ áp dụng cho luật giới hạn người merge. Các điều kiện review và CI nằm ở ruleset riêng, áp dụng cả với chủ repo.

Xem [luật PR, review và CI](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677767) và [luật giới hạn người merge](https://github.com/DTU-TEAM-2/smart-cafe-management/rules/26677768).

CI hiện kiểm tra tài liệu và quy trình. Khi đưa ứng dụng vào repo, nhóm bổ sung build/lint/test theo phần code mới rồi cập nhật các check bắt buộc.
