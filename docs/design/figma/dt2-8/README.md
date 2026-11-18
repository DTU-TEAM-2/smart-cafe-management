# Tham chiếu Figma local cho DT2-8

Gói này lưu nguyên bản `.fig` và hai PNG nguồn do người dùng cung cấp ngày 18/11/2026. Có thể mở PNG để đối chiếu UI ngay trong workspace. File `.fig` chứa toàn bộ prototype LECKER, hai ảnh chỉ bao phủ màn hóa đơn và thống kê desktop của DT2-8.

- [Manifest nguồn, node ID, kích thước và SHA-256](manifest.json)
- [Bản Figma gốc](lecker-smart-cafe.fig)
- [Staff · Hóa đơn, node 8:66](staff-bills.png)
- [Admin · Thống kê doanh thu, node 8:427](admin-revenue.png)
- [Quyết định QA SRS áp dụng](../../../handover/dt2-8-qa.md)

Khi làm UI DT2-8, đọc manifest và ảnh local trước. Dùng Figma khi cần kiểm tra thuộc tính sâu hơn hoặc khi chủ thiết kế báo có phiên bản mới. Khi cập nhật nguồn, thay nguyên bộ export tương ứng rồi cập nhật hash trong manifest. Giữ ghi nhận nguồn, ngày xuất và các quyết định nghiệp vụ.

Theo yêu cầu ngày 18/11/2026, biểu đồ đang triển khai tiếp tục theo QA SRS 22. Bộ lọc, phân trang và trường người lập hóa đơn theo SRS 6.1.9. QA 21 quy định quyền Staff/Admin. Các số tiền, tên món và số hóa đơn trong ảnh là nội dung mẫu thiết kế.

Sidebar ứng dụng liên kết những route đã tích hợp trong nhánh hiện tại. Các module Menu, Nhân viên, Phản hồi và Tin tức cần phối hợp với task phụ trách khi tích hợp đầy đủ điều hướng Admin. Bản thân gói tham chiếu không tạo các route đó.

Kiểm tra tính toàn vẹn bằng Python tại thư mục này.

```python
import hashlib
import json
from pathlib import Path

manifest = json.loads(Path("manifest.json").read_text(encoding="utf-8"))
for artifact in manifest["artifacts"]:
    content = Path(artifact["path"]).read_bytes()
    assert len(content) == artifact["bytes"], artifact["path"]
    assert hashlib.sha256(content).hexdigest() == artifact["sha256"], artifact["path"]
print("Figma references verified")
```
