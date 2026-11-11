"""Render recorded HTTP observations; never invent results or mark blocked cases as passed."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
from html import escape
import json
from pathlib import Path


def unique(values):
    return list(dict.fromkeys(str(value) for value in values))


def render(report):
    cases = report["cases"]
    counts = Counter(case["result"] for case in cases)
    if any(case["result"] not in {"PASS", "FAIL", "BLOCKED"} for case in cases):
        raise ValueError("Unknown case result")
    timestamp = datetime.fromisoformat(report["generatedAt"].replace("Z", "+00:00"))
    local = timestamp.astimezone(timezone(timedelta(hours=7)))
    rows = []
    observations_count = 0
    for case in cases:
        observations = case.get("observations", [])
        observations_count += sum(observation["actual"].isdigit() for observation in observations)
        expected = " · ".join(unique(observation["expected"] for observation in observations)) or "—"
        actual = " · ".join(unique(observation["actual"] for observation in observations)) or "Không có response"
        first_request = observations[0]["request"] if observations else "Không có request"
        request_text = escape(first_request)
        if len(observations) > 1:
            checks = "".join(
                f'<li><code>{escape(observation["request"])}</code><span>'
                f'{escape(str(observation["expected"]))} → {escape(str(observation["actual"]))}</span></li>'
                for observation in observations
            )
            request_text = f'<details><summary><code>{request_text}</code><small>+ {len(observations) - 1} request</small></summary><ul>{checks}</ul></details>'
        else:
            request_text = f'<code>{request_text}</code>'
        result = case["result"]
        mode = '<small class="test-mode">PROVIDER GIẢ LẬP</small>' if case.get("mode") == "PROVIDER_STUB" else ''
        rows.append(
            f'<tr><td class="case-id">{escape(case["id"])}</td><th scope="row">{escape(case["title"])}</th>'
            f'<td class="request">{request_text}</td><td class="status">{escape(expected)}</td>'
            f'<td class="status">{escape(actual)}</td><td><span class="badge {result.lower()}">{result}</span>{mode}'
            f'<span class="sr-only">. {escape(case.get("note", ""))}</span></td></tr>'
        )
    blocked = "".join(f'<li><strong>{escape(case["id"])}</strong> — {escape(case["note"])}</li>' for case in cases if case["result"] == "BLOCKED")
    provider = escape(report.get("provider", "Cloudinary thật chưa được kiểm thử trong lượt này."))
    badge_class = "fail" if counts["FAIL"] else "pass"
    return f'''<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DT2-9 · Kết quả kiểm thử API thực đơn</title>
<style>
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: #ecf3f7; color: #123446; font: 14px/1.45 "Segoe UI", Arial, sans-serif; }}
main {{ max-width: 1440px; margin: auto; padding: 28px 36px; }}
.hero {{ background: #142e3b; color: white; border-radius: 14px; padding: 22px 24px; display: flex; align-items: center; justify-content: space-between; gap: 24px; }}
.eyebrow {{ color: #d3e5ef; font-size: 13px; margin: 0 0 4px; }}
h1 {{ margin: 0 0 4px; font-size: 29px; line-height: 1.4; }}
.subtitle {{ margin: 0; color: #d3e5ef; font-size: 13px; }}
.totals {{ text-align: center; flex-shrink: 0; }}
.score {{ display: block; padding: 12px 20px; border-radius: 12px; font-size: 25px; font-weight: 750; letter-spacing: .02em; }}
.totals small {{ color: #ffdf9b; display: block; margin-top: 7px; }}
.pass {{ background: #d8f4e7; color: #125541; }}
.fail {{ background: #ffe4e4; color: #8b2020; }}
.blocked {{ background: #fff0ca; color: #704800; }}
.note {{ background: #fffaf0; border: 1px solid #e5d5ae; border-radius: 10px; padding: 13px 18px; margin: 16px 0; color: #59451e; font-size: 13px; }}
.panel {{ background: white; border: 1px solid #cadce6; border-radius: 13px; overflow: hidden; }}
.panel-heading {{ padding: 16px 18px 10px; }}
h2 {{ font-size: 19px; margin: 0 0 4px; color: #102e3d; }}
.panel-heading p {{ margin: 0; color: #41687d; font-size: 12px; }}
.table-scroll {{ position: relative; overflow-x: auto; }}
table {{ border-collapse: collapse; width: 100%; min-width: 1050px; }}
thead {{ background: #e5eef3; color: #244a60; }}
th, td {{ padding: 8px 10px; border-bottom: 1px solid #e1ebf0; vertical-align: top; text-align: left; }}
thead th {{ font-size: 12px; font-weight: 700; }}
tbody th {{ font-weight: 400; width: 28%; }}
tbody tr:nth-child(even) {{ background: #f6fafc; }}
tbody tr:last-child th, tbody tr:last-child td {{ border-bottom: 0; }}
.case-id {{ white-space: nowrap; color: #325c73; font-size: 12px; }}
.request {{ width: 36%; max-width: 540px; overflow-wrap: anywhere; }}
code {{ font: 11px/1.6 Consolas, monospace; color: #28536d; }}
.status {{ text-align: center; font-variant-numeric: tabular-nums; font-size: 12px; min-width: 82px; }}
.badge {{ font-size: 10px; font-weight: 800; padding: 2px 6px; border-radius: 3px; white-space: nowrap; }}
.test-mode {{ display: block; color: #315d75; font-size: 9px; margin-top: 5px; font-weight: 700; }}
details summary {{ cursor: pointer; }}
details small {{ color: #58778a; display: block; margin-left: 14px; font-size: 10px; }}
details ul {{ padding-left: 16px; margin: 8px 0; }}
details li {{ margin-bottom: 6px; }}
details li span {{ display: block; font-size: 11px; color: #426275; }}
details summary:focus-visible {{ outline: 2px solid #186b91; outline-offset: 3px; }}
.limitations {{ background: white; border: 1px solid #cadce6; border-radius: 12px; padding: 16px 18px; margin-top: 16px; }}
.limitations h2 {{ font-size: 16px; }}
.limitations ul {{ margin: 10px 0 0; padding-left: 20px; color: #476779; font-size: 13px; }}
footer {{ color: #476779; font-size: 12px; margin-top: 12px; }}
.sr-only {{ position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }}
@media (max-width: 700px) {{ main {{ padding: 16px 12px; }} .hero {{ align-items: flex-start; flex-direction: column; padding: 20px; }} h1 {{ font-size: 24px; }} .totals {{ text-align: left; }} }}
@media print {{ main {{ padding: 0; }} .table-scroll {{ overflow: visible; }} table {{ min-width: 0; }} .hero, .badge, thead {{ print-color-adjust: exact; }} }}
</style>
</head>
<body><main>
<header class="hero"><div><p class="eyebrow">SMART CAFE · DT2-9 · {local:%d/%m/%Y}</p><h1>Kết quả kiểm thử API</h1><p class="subtitle">{escape(report["transport"])}</p></div>
<div class="totals"><strong class="score {badge_class}">{counts["PASS"]} / {len(cases)} PASS</strong><small>{counts["FAIL"]} FAIL · {counts["BLOCKED"]} BLOCKED</small></div></header>
<aside class="note"><strong>Điều kiện xác thực:</strong> {escape(report["authentication"])}<br><strong>Provider ảnh:</strong> {provider}<br>Đây là lượt chạy tự động bằng Java HTTP client, không phải kết quả chạy trong Postman.</aside>
<section class="panel" aria-labelledby="report-title"><div class="panel-heading"><h2 id="report-title">Quản lý món và nhóm món</h2><p>46 mục theo trang testcase · {observations_count} HTTP response thực tế · PASS khi mã HTTP và assertion dữ liệu đạt kỳ vọng. Bấm request có dấu mở rộng để xem từng biến thể.</p></div>
<div class="table-scroll" tabindex="0" role="region" aria-label="Bảng kết quả kiểm thử API"><table><caption class="sr-only">Kết quả thực thi các ca DT2-9</caption><thead><tr><th scope="col">#</th><th scope="col">Ca kiểm thử</th><th scope="col">Request</th><th scope="col">Dự kiến</th><th scope="col">Thực tế</th><th scope="col">Kết quả</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div></section>
<section class="limitations"><h2>Phạm vi và giới hạn kiểm thử</h2><ul>{blocked}<li>{provider} Chưa xác nhận upload hoặc lỗi trên Cloudinary thật; kết quả provider giả lập không thay smoke provider thật.</li><li>TC-40 đối chiếu DB read-only sau khi xóa: không phải request HTTP. Các ca nhiều request được gộp một dòng; bấm mở rộng để xem mã HTTP từng request.</li><li>Bearer ADMIN/STAFF do test ký bằng HMAC theo contract auth chung; quyền lấy từ users trong DB test. Không chứng minh đăng nhập hoặc phát hành token production.</li><li>Dữ liệu fixture trong schema *_test riêng, chỉ dọn dữ liệu của lượt báo cáo sau hậu kiểm. Không sửa database legacy. Mã 4xx trong ca âm là phản hồi dự kiến.</li></ul></section>
<footer>Chạy lúc {local:%H:%M:%S %d/%m/%Y} (Asia/Saigon) · Nguồn máy đọc: <a href="results.json">results.json</a> · Báo cáo tĩnh, không gọi API và không chứa token/cookie/key.</footer>
</main></body></html>'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    report = json.loads(args.source.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(report), encoding="utf-8")
    (args.output.parent / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(Counter(case["result"] for case in report["cases"]))))


if __name__ == "__main__":
    main()
