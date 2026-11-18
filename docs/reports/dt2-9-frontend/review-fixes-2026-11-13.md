# PR #13 — xử lý review ngày 13/11/2026

Chủ repo yêu cầu tắt preview, sửa comment/unresolved và trả lời comment đã sửa theo mẫu “đã khắc phục: …”. Đã dừng Vite local cổng 5173; server dev/production được Playwright mở tạm trong kiểm thử và tự dừng, không để preview chạy sau bàn giao.

## Findings

| Thread/comment | Xử lý |
| --- | --- |
| 4558784295 — giới hạn pixel sau decode | Thêm imageDimensions.js đọc PNG IHDR/JPEG SOF có kiểm bounds/segment/trùng frame. Chặn >20 triệu pixel trước createImageBitmap; vẫn decode kiểm tính hợp lệ bên trong giới hạn và kiểm tra kích thước sau decode. Test spy xác nhận decoder không được gọi cho PNG/JPEG header nhỏ khai báo kích thước cực lớn, header hỏng/truncated, và PNG nén <5 MiB nhưng raster >20 triệu pixel; JPEG thật vẫn nhận. |
| 4558795816 — preview có trong production | Guard import và route bằng import.meta.env.DEV. Production build không có chunk MenuManagement/fixture; test browser trên vite preview xác nhận không có UI demo tại /preview/menu. Dev vẫn dùng được đường dẫn cũ. |
| 4558795809 — case/accent và literal | Làm rõ contract/QA theo SQL và collation utf8mb4_0900_ai_ci đã có, không đổi schema/query nghiệp vụ: name/category không phân biệt hoa/dấu; %, _, ! literal, không mở rộng SQL wildcard. Thêm regression MySQL cho bac xiu/BẠC XỈU và CA PHE cùng các ký tự literal, FE kiểm cùng biến thể. Không nhận fixture normalize là nguồn quyền cho dữ liệu production. |
| 4558784300 — đồng bộ Git Flow | PR #12 ban đầu Open, sau đó được maintainer merge trong lúc xử lý review. Đã tích hợp develop 5cc4132, giữ Git Flow job/validator/CODEOWNERS và fixture bán hàng của baseline mới; chỉ giữ sửa readiness MySQL qua TCP. Chạy lại checks trên revision mới, không lấy kết quả bốn check cũ thay Git Flow. Không thay ruleset, tự approval hoặc bypass protection. |

Follow-up DT2-64 nối API/auth/media thật được ghi tại [checklist bàn giao](../../handover/dt2-64-menu-api-follow-up.md), đầu mối FE là tác giả PR; owner sprint cần nhóm xác nhận. Chưa triển khai follow-up này và không tick Done dựa trên preview. Giữ nguyên evidence ảnh cũ có provenance; không xóa ảnh chỉ để giảm diff của lịch sử review.

## Kiểm chứng

- npm run lint, npm run build đạt. Production bundle không chứa menu preview/fixture.
- Toàn bộ Playwright: 54/54 đạt trên desktop/mobile Chromium; chạy lại 4/4 ca review sau khi bổ sung PNG nén thật vượt 20 triệu pixel. Browser/device emulator không thay Safari thiết bị thật.
- Maven -o -B -ntp verify: 62 JUnit, 0 failures/errors/skips, MySQL 8.0.46 cổng 13369, schema smart_cafe_merge_test riêng có guard. Backend production, DDL/schema.sql không thay đổi. DB nghiệp vụ local không bị chỉnh sửa.
- Registry 17/16 và checksum 169 inherited files; sau đồng bộ baseline mới chạy lại Git Flow + routing tests; authored whitespace check loại snapshots inherited. Không có Aikido MCP khả dụng để chạy SAST.
- CI GitHub và reviewer xác nhận sau push là bằng chứng riêng; không tự biến trả lời finding thành approval hoặc resolve của reviewer. Không merge PR khác hoặc PR #13.

Skills đã đọc: cafe-review/cafe-quality tại .agents/skills cho review/verification; vercel-react-best-practices cho DEV guard và state bất đồng bộ; cafe-spring-api cho contract/kiểm thử MySQL. Nguồn kỹ thuật header-only: [W3C PNG IHDR](https://www.w3.org/TR/png/#11IHDR), [JPEG marker layout trong libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/main/src/jdmarker.c) chỉ để đối chiếu format, không sao chép implementation upstream. Parser chỉ preflight kích thước, browser decoder vẫn quyết định file có hợp lệ; không coi parser là validator ảnh production.
