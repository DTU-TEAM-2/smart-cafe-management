"""Render recorded HTTP observations for DT2-10 Staff Management."""
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
    for idx, case in enumerate(cases, start=1):
        observations = case.get("observations", [])
        observations_count += sum(observation["actual"].isdigit() for observation in observations)
        expected = " · ".join(unique(observation["expected"] for observation in observations)) or "—"
        actual = " · ".join(unique(observation["actual"] for observation in observations)) or "Không có response"
        first_request = observations[0]["request"] if observations else "Không có request"

        # Format request with method highlight
        parts = first_request.split(" ", 1)
        if len(parts) == 2:
            method, path = parts
            request_text = f'<span class="method {method.lower()}">{method}</span> <span class="path">{escape(path)}</span>'
        else:
            request_text = f'<code>{escape(first_request)}</code>'

        result = case["result"]
        badge_class = result.lower()
        rows.append(
            f'<tr><td class="case-num">{idx}</td>'
            f'<th scope="row" class="case-title">{escape(case["title"])}</th>'
            f'<td class="request">{request_text}</td>'
            f'<td class="status expected">{escape(expected)}</td>'
            f'<td class="status actual">{escape(actual)}</td>'
            f'<td class="result"><span class="badge {badge_class}">{result}</span>'
            f'<span class="sr-only">. {escape(case.get("note", ""))}</span></td></tr>'
        )

    badge_class = "fail" if counts["FAIL"] else "pass"
    pass_count = counts.get("PASS", 0)
    total_count = len(cases)

    return f'''<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SMART CAFE · DT2-10 · Kết quả kiểm thử API Quản lý nhân viên</title>
<style>
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: #eef2f6; color: #1e293b; font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, "Helvetica Neue", Arial, sans-serif; }}
main {{ max-width: 1200px; margin: 30px auto; padding: 0 20px; }}
.hero {{ background: #0f172a; color: white; border-radius: 12px; padding: 24px 28px; display: flex; align-items: center; justify-content: space-between; gap: 24px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); }}
.eyebrow {{ color: #94a3b8; font-size: 13px; font-weight: 500; letter-spacing: 0.05em; margin: 0 0 6px; text-transform: uppercase; }}
h1 {{ margin: 0 0 6px; font-size: 26px; font-weight: 700; letter-spacing: -0.02em; }}
.subtitle {{ margin: 0; color: #94a3b8; font-size: 13px; }}
.score-badge {{ background: #dcfce7; color: #15803d; padding: 12px 24px; border-radius: 10px; font-size: 22px; font-weight: 800; letter-spacing: 0.03em; border: 1px solid #bbf7d0; white-space: nowrap; }}
.score-badge.fail {{ background: #fee2e2; color: #b91c1c; border-color: #fecaca; }}
.panel {{ background: white; border: 1px solid #e2e8f0; border-radius: 12px; margin-top: 24px; overflow: hidden; box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05); }}
.panel-heading {{ padding: 20px 24px 16px; border-bottom: 1px solid #f1f5f9; }}
h2 {{ font-size: 17px; font-weight: 700; margin: 0 0 4px; color: #0f172a; }}
.panel-heading p {{ margin: 0; color: #64748b; font-size: 13px; }}
.table-scroll {{ overflow-x: auto; }}
table {{ border-collapse: collapse; width: 100%; min-width: 900px; }}
thead {{ background: #f8fafc; border-bottom: 1px solid #e2e8f0; }}
th, td {{ padding: 12px 16px; text-align: left; vertical-align: middle; }}
thead th {{ font-size: 12px; font-weight: 600; color: #475569; text-transform: uppercase; letter-spacing: 0.04em; }}
tbody tr {{ border-bottom: 1px solid #f1f5f9; transition: background-color 0.1s; }}
tbody tr:hover {{ background-color: #f8fafc; }}
tbody tr:last-child {{ border-bottom: 0; }}
.case-num {{ width: 44px; text-align: center; color: #94a3b8; font-size: 13px; font-variant-numeric: tabular-nums; }}
.case-title {{ font-weight: 500; font-size: 14px; color: #1e293b; width: 34%; }}
.request {{ font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 13px; }}
.method {{ font-weight: 700; font-size: 11px; padding: 2px 6px; border-radius: 4px; display: inline-block; min-width: 48px; text-align: center; }}
.method.get {{ color: #0284c7; background: #e0f2fe; }}
.method.post {{ color: #16a34a; background: #dcfce7; }}
.method.put {{ color: #d97706; background: #fef3c7; }}
.method.delete {{ color: #dc2626; background: #fee2e2; }}
.path {{ color: #334155; margin-left: 6px; }}
.status {{ text-align: center; font-variant-numeric: tabular-nums; font-weight: 500; font-size: 13px; width: 90px; }}
.status.expected {{ color: #64748b; }}
.status.actual {{ color: #0f172a; font-weight: 600; }}
.result {{ text-align: center; width: 90px; }}
.badge {{ font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 6px; display: inline-block; letter-spacing: 0.05em; }}
.badge.pass {{ background: #dcfce7; color: #15803d; }}
.badge.fail {{ background: #fee2e2; color: #b91c1c; }}
footer {{ margin-top: 16px; padding: 0 4px; color: #64748b; font-size: 12px; display: flex; justify-content: space-between; align-items: center; }}
.sr-only {{ position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }}
</style>
</head>
<body>
<main>
  <header class="hero">
    <div>
      <p class="eyebrow">SMART CAFE · DT2-10 · {local:%d/%m/%Y}</p>
      <h1>Kết quả kiểm thử API</h1>
      <p class="subtitle">{escape(report["transport"])}</p>
    </div>
    <div class="score-badge {badge_class}">{pass_count} / {total_count} PASS</div>
  </header>

  <section class="panel" aria-labelledby="heading-cases">
    <div class="panel-heading">
      <h2 id="heading-cases">Quản lý nhân viên (Thêm, Sửa, Xóa, Tìm kiếm &amp; Phân trang)</h2>
      <p>Ca 1–{total_count} · Tiền tố /api/v1 · PASS khi mã HTTP và dữ liệu kiểm tra khớp kỳ vọng.</p>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th scope="col" class="case-num">#</th>
            <th scope="col">Ca kiểm thử</th>
            <th scope="col">Request</th>
            <th scope="col" class="status">Dự kiến</th>
            <th scope="col" class="status">Thực tế</th>
            <th scope="col" class="result">Kết quả</th>
          </tr>
        </thead>
        <tbody>
          {''.join(rows)}
        </tbody>
      </table>
    </div>
  </section>

  <footer>
    <span>Dữ liệu kiểm thử trên MySQL thật (schema test cách ly). Mã 4xx trong các ca âm là phản hồi dự kiến.</span>
    <span>Sinh tự động lúc {local:%H:%M:%S %d/%m/%Y}</span>
  </footer>
</main>
</body>
</html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", default="target/api-report/dt2-10-api-results.json", type=Path)
    parser.add_argument("--output", default="docs/reports/dt2-10-api/report.html", type=Path)
    args = parser.parse_args()

    input_path = args.input
    if not input_path.exists() and (Path("backend") / input_path).exists():
        input_path = Path("backend") / input_path

    report = json.loads(input_path.read_text(encoding="utf-8"))
    html = render(report)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(html, encoding="utf-8")
    (args.output.parent / "results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote report to {args.output}")


if __name__ == "__main__":
    main()
