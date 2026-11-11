"""Render actual Newman responses; no invented statuses or Postman UI imitation."""
import argparse
import base64
from datetime import datetime, timezone
from html import escape
import json
from pathlib import Path
from urllib.parse import urlencode


def summarize(source, revision, run_url):
    run = source['run']
    cases = []
    for execution in run['executions']:
        request = execution['request']
        url = request['url']
        if isinstance(url, dict):
            raw = url.get('raw')
            if not raw:
                query = urlencode([(q['key'], q.get('value', '')) for q in url.get('query', []) if not q.get('disabled')])
                raw = '/' + '/'.join(url.get('path', [])) + ('?' + query if query else '')
            url = raw
        response = execution.get('response') or {}
        stream = response.get('stream', {})
        body = bytes(stream.get('data', [])).decode('utf-8', errors='replace') if isinstance(stream, dict) else str(stream)
        try:
            body = json.loads(body)
        except json.JSONDecodeError:
            pass
        assertions = [{'name': a['assertion'], 'passed': not a.get('error') and not a.get('skipped')}
                      for a in execution.get('assertions', [])]
        cases.append({'name': execution['item']['name'], 'method': request['method'],
                      'url': url, 'status': response.get('code'), 'responseTimeMs': response.get('responseTime'),
                      'body': body, 'assertions': assertions,
                      'passed': bool(response) and bool(assertions) and all(a['passed'] for a in assertions)})
    return {'generatedAt': datetime.now(timezone.utc).isoformat(), 'revision': revision,
            'runUrl': run_url, 'runner': 'Newman 6.2.1 / Postman Collection v2.1',
            'environment': 'GitHub Actions / Spring Boot 4.1.2 / JDK 21 / MySQL 8.0 / smart_cafe_postman_test',
            'fixture': 'Synthetic menu rows only; existing schema.sql fixture, not production DDL.',
            'stats': run['stats'], 'failureCount': len(run.get('failures', [])), 'cases': cases}


def render(report, cases, title):
    cards = []
    for case in cases:
        tests = ''.join(f'<li>{"PASS" if a["passed"] else "FAIL"} — {escape(a["name"])}</li>' for a in case['assertions'])
        response = json.dumps(case['body'], ensure_ascii=False, indent=2)
        cards.append(f'<article><h2>{escape(case["name"])}</h2><div class="request">'
                     f'{escape(case["method"])} {escape(case["url"])}</div>'
                     f'<p><b class="{"pass" if case["passed"] else "fail"}">{"PASS" if case["passed"] else "FAIL"}</b>'
                     f' · HTTP {case["status"]} · {case["responseTimeMs"]} ms</p>'
                     f'<div class="columns"><pre>{escape(response)}</pre><ul>{tests}</ul></div></article>')
    passed = sum(c['passed'] for c in report['cases'])
    total = len(report['cases'])
    assertions = sum(len(c['assertions']) for c in report['cases'])
    return f'''<!doctype html><html lang="vi"><meta charset="utf-8"><title>{escape(title)}</title>
<style>
* {{box-sizing:border-box}} body {{margin:0;background:#eef2f6;color:#182e42;font:16px/1.5 "Segoe UI",Arial,sans-serif}}
main {{max-width:1440px;margin:auto;padding:28px}} header {{background:#183449;color:white;padding:24px;border-radius:12px}}
h1 {{font-size:27px;margin:0 0 10px}} h2 {{font-size:19px;margin:0 0 12px}} header p {{margin:6px 0}}
.note {{background:#fff7dd;border:1px solid #e5ce80;padding:16px;border-radius:8px;margin:18px 0}}
article {{background:white;border:1px solid #cfdae4;padding:22px;border-radius:10px;margin:18px 0;break-inside:avoid}}
.request {{background:#eaf0f6;padding:10px;font-family:Consolas,monospace;overflow-wrap:anywhere}}
.columns {{display:grid;grid-template-columns:1.1fr 1fr;gap:24px}} pre {{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.5 Consolas,monospace;margin:0;background:#f4f7fa;padding:16px}}
ul {{padding-left:22px;margin:0}} li {{padding:4px 0}} .pass {{color:#0e6b45}} .fail {{color:#a32222}}
small {{overflow-wrap:anywhere;display:block}} a {{color:inherit}}
</style><main><header><h1>{escape(title)}</h1><p>{passed}/{total} requests đạt · {assertions} assertions · {report['failureCount']} failures</p>
<p>{escape(report['runner'])}</p><small>{escape(report['environment'])}</small>
<small>Revision: {escape(report['revision'])} · Báo cáo UTC: {escape(report['generatedAt'])}</small>
<small>CI: {escape(report['runUrl'])}</small></header>
<div class="note">Báo cáo từ HTTP response và assertion thực tế của Postman Collection chạy bằng Newman.
Không phải ảnh chụp ứng dụng Postman Desktop. Dữ liệu tổng hợp chỉ trong MySQL test biệt lập; không dùng dữ liệu production.
Fixture schema kiểm thử không chứng minh DDL production. Chỉ kiểm thử chức năng Menu; không kết luận 7 chức năng DT2-4 còn lại.</div>
{''.join(cards)}</main></html>'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--revision', default='')
    parser.add_argument('--run-url', default='')
    parser.add_argument('--emit-log', action='store_true')
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding='utf-8'))
    report = summarize(source, args.revision, args.run_url) if 'run' in source else source
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    groups = [('index', 'DT2-4 · Kết quả kiểm thử Postman / Newman', report['cases']),
              ('01-categories', 'DT2-4 · API danh mục món', [c for c in report['cases'] if '/categories' in c['url']]),
              ('02-list-filter', 'DT2-4 · Danh sách, lọc, tìm kiếm và phân trang', [c for c in report['cases'] if c['status'] == 200 and '/items' in c['url'] and '/items/' not in c['url']]),
              ('03-detail', 'DT2-4 · API chi tiết món', [c for c in report['cases'] if c['status'] == 200 and '/items/' in c['url']]),
              ('04-errors', 'DT2-4 · Validation, không tồn tại và từ chối thao tác ghi', [c for c in report['cases'] if c['status'] is None or c['status'] >= 400])]
    for name, title, cases in groups:
        (args.output / f'{name}.html').write_text(render(report, cases, title), encoding='utf-8')
    if args.emit_log:
        # Only public synthetic menu request/response data; excludes headers, cookies and environment secrets.
        payload = base64.b64encode(json.dumps(report, ensure_ascii=False).encode('utf-8')).decode('ascii')
        print('DT2_4_POSTMAN_REPORT=' + payload)


if __name__ == '__main__':
    main()
