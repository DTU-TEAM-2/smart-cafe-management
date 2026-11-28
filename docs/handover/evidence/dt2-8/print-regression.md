# DT2-8 — Print regression (25/11/2026)

Review: [P2 long item names clip quantity/prices](https://github.com/DTU-TEAM-2/smart-cafe-management/pull/18#discussion_r4691934933). Baseline `bb8ba8d`, merged develop `9a88ba6`.

- Actual React route `/#bills`, authenticated ADMIN mocked HTTP list/detail; open detail via UI. Chromium `page.pdf()` A4, 12 mm margins, desktop and mobile projects. The fixture contains names of 160 and 80 unbroken characters, 18-digit VND values as strings, and 150 additional uniquely named lines (152 total). No real customer data.
- Before: both PDFs 4 pages, missing complete 160-character name and exact unit price; first-page render visibly loses quantity, unit price and line total.
- After: both PDFs 5 pages, all 152 names, exact VND values, quantity and grand total, repeated headers, no background application UI. Test inspects actual PDF text and horizontal text bounds with pinned dev-only PDF.js; independently extracted with PyMuPDF and visually inspected first-page PNG before/after. Targeted Playwright 2/2 pass on Windows (desktop and mobile).
- CSS uses explicit column proportions only for print, wraps long content without truncation, removes dialog height/width caps and scroll clipping, keeps rows together and repeats thead. No API, permission, monetary arithmetic, database schema or approved chart changes.

Evidence: [before first page](print-before-page1.png), [after first page](print-after-page1.png), [complete A4 sample](print-after-a4.pdf). The sample is synthetic; physical printer and non-A4 paper sizes are not validated, pending owner/UAT. The previous commit's backend and CI results are not attributed to this new commit.
