import { useState, useEffect } from "react";
import { fetchRevenueStatistics, vnd } from "./billingApi.js";

const PERIODS = [
  { key: "today", label: "Hôm nay (Today)" },
  { key: "in_week", label: "Tuần này (In Week)" },
  { key: "in_month", label: "Tháng này (In Month)" },
  { key: "in_year", label: "Năm nay (In Year)" },
];

const PAYMENT_METHODS = {
  CASH: { label: "Tiền mặt", color: "#d5a953" },
  BANK_TRANSFER: { label: "Chuyển khoản", color: "#7bb8a0" },
  UNKNOWN: { label: "Chưa rõ phương thức", color: "#9c8b7a" },
};
const percent = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 1 });

function PaymentChart({ methods, totalRevenue }) {
  const total = BigInt(totalRevenue);
  let cumulative = 0n;
  const entries = methods.map(({ method, revenue }) => {
    const amount = BigInt(revenue);
    const start = total > 0n ? Number(cumulative * 100000n / total) / 1000 : 0;
    cumulative += amount;
    const end = total > 0n ? Number(cumulative * 100000n / total) / 1000 : 0;
    const share = total > 0n ? Number((amount * 1000n + total / 2n) / total) / 10 : 0;
    return { method, revenue, start, end, share, ...(PAYMENT_METHODS[method] ?? PAYMENT_METHODS.UNKNOWN) };
  });
  const stops = entries.filter(entry => entry.end > entry.start)
    .map(({ color, start, end }) => `${color} ${start}% ${end}%`);

  return (
    <section className="billing-chart-panel" aria-labelledby="payment-chart-title">
      <h2 id="payment-chart-title">Doanh thu theo phương thức thanh toán</h2>
      {total === 0n ? <p className="subtitle">Chưa có doanh thu để tính tỷ trọng.</p> : (
        <div className="billing-payment-chart">
          {stops.length > 0 && <div className="billing-pie" aria-hidden="true"
            style={{ background: `conic-gradient(${stops.join(", ")})` }} />}
          <ul aria-label="Tỷ trọng doanh thu theo phương thức thanh toán">
            {entries.map(({ method, label, color, revenue, share }) => (
              <li key={method}>
                <span className="billing-pie-swatch" style={{ backgroundColor: color }} aria-hidden="true" />
                <span>{label}</span>
                <strong>{vnd(revenue)} · {percent.format(share)}%</strong>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function chartBuckets(days) {
  if (days.length <= 31) return days.map(({ date, revenue, billCount }) => ({
    key: date, label: date.slice(5), amount: BigInt(revenue), billCount,
  }));

  const months = new Map();
  for (const { date, revenue, billCount } of days) {
    const month = date.slice(0, 7);
    const bucket = months.get(month) ?? {
      key: month, label: `${month.slice(5)}/${month.slice(0, 4)}`, amount: 0n, billCount: 0,
    };
    bucket.amount += BigInt(revenue);
    bucket.billCount += billCount;
    months.set(month, bucket);
  }
  return [...months.values()];
}

export default function StatisticsView({ authorization, credentials }) {
  const [query, setQuery] = useState({ period: "in_month" });
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [validation, setValidation] = useState("");
  const [data, setData] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    async function loadStats() {
      try {
        const result = await fetchRevenueStatistics({ ...query, authorization, credentials, signal: controller.signal });
        if (!controller.signal.aborted) setData(result);
      } catch (error) {
        if (!controller.signal.aborted) setError(error.message === "UNAUTHORIZED"
          ? "Bạn không có quyền xem số liệu thống kê."
          : error.message);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void loadStats();
    return () => controller.abort();
  }, [query, authorization, credentials]);

  function load(nextQuery) {
    setValidation("");
    setError("");
    setData(null);
    setLoading(true);
    setQuery({ ...nextQuery });
  }

  function handleCustomSearch(event) {
    event.preventDefault();
    if (!dateFrom || !dateTo) {
      setValidation("Vui lòng chọn cả ngày bắt đầu và kết thúc.");
      return;
    }
    if (dateFrom > dateTo) {
      setValidation("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
      return;
    }
    load({ from: dateFrom, to: dateTo });
  }

  const summary = data?.summary;
  const daily = data?.dailyRevenue ?? [];
  const buckets = chartBuckets(daily);
  const maxRevenue = buckets.reduce((max, bucket) => bucket.amount > max ? bucket.amount : max, 1n);
  const unit = daily.length > 31 ? "tháng" : "ngày";

  return (
    <div className="billing-statistics-section">
      <div className="billing-control-panel">
        <div className="billing-search-types" role="radiogroup" aria-label="Kỳ thống kê">
          {PERIODS.map((p) => (
            <label key={p.key}>
              <input
                type="radio"
                name="stat_period"
                checked={query.period === p.key}
                onChange={() => load({ period: p.key })}
              />
              {p.label}
            </label>
          ))}
        </div>

        <form className="billing-search-form" onSubmit={handleCustomSearch}>
          <label style={{ display: "inline-flex", alignItems: "center", gap: "8px", fontSize: "13px" }}>
            Từ ngày:
            <input
              type="date"
              className="billing-input"
              style={{ minWidth: "140px" }}
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
            />
          </label>
          <label style={{ display: "inline-flex", alignItems: "center", gap: "8px", fontSize: "13px" }}>
            Đến ngày:
            <input
              type="date"
              className="billing-input"
              style={{ minWidth: "140px" }}
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
            />
          </label>
          <button type="submit" className="billing-button primary">
            Thống kê (Count)
          </button>
        </form>
      </div>

      {(validation || error) && (
        <div className="billing-message-banner error" role="alert" style={{ marginBottom: "20px" }}>
          <span>{validation || error}</span>
          {!validation && <button type="button" className="billing-button secondary" onClick={() => load(query)}>Thử lại</button>}
        </div>
      )}

      {loading && <p role="status">Đang tải số liệu thống kê...</p>}

      {!loading && summary && (
        <>
          {/* KPI Metrics Cards */}
          <div className="billing-metrics-grid">
            <div className="billing-metric-card">
              <span className="label">Tổng doanh thu ({summary.from} → {summary.to})</span>
              <span className="value">{vnd(summary.totalRevenue)}</span>
            </div>
            <div className="billing-metric-card">
              <span className="label">Tổng số hóa đơn</span>
              <span className="value">{summary.totalBills}</span>
            </div>
            <div className="billing-metric-card">
              <span className="label">Giá trị hóa đơn trung bình</span>
              <span className="value">{vnd(summary.averageBillAmount)}</span>
            </div>
          </div>

          <section className="billing-chart-panel" aria-labelledby="column-chart-title">
            <h2 id="column-chart-title">Biểu đồ doanh thu</h2>
            <p className="subtitle">
              Doanh thu theo {unit} trong kỳ ({summary.from} đến {summary.to}) từ hóa đơn đã thanh toán (PAID).
            </p>

            {summary.totalBills === 0 ? (
              <p style={{ color: "var(--muted)", textAlign: "center", padding: "30px 0" }}>
                Không có dữ liệu trong khoảng thời gian này.
              </p>
            ) : (
              <div className="billing-chart-scroll" role="region" aria-label="Vùng cuộn biểu đồ doanh thu" tabIndex={0}>
                <div className="billing-column-chart" role="img" aria-label={`Biểu đồ cột: Doanh thu theo ${unit}`}>
                  {buckets.map(({ key, label, amount, billCount }) => {
                    const height = Number(amount * 10000n / maxRevenue) / 100;
                    return (
                      <div key={key} className="billing-column-bar-group" title={`${key}: ${vnd(amount)} (${billCount} hóa đơn)`}>
                        <div className="billing-column-bar" style={{ height: `${height}%`, minHeight: amount > 0n ? 1 : 0 }} />
                        <span className="billing-column-label">{label}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </section>

          <PaymentChart methods={data.revenueByPaymentMethod} totalRevenue={summary.totalRevenue} />

          {/* Accessible Table for Screen Readers & Verification */}
          <details style={{ marginTop: "16px", color: "var(--muted)", fontSize: "13px" }}>
            <summary style={{ cursor: "pointer", fontWeight: 600 }}>
              Xem bảng đối soát dữ liệu chi tiết
            </summary>
            <div className="billing-audit-scroll" role="region" aria-label="Bảng đối soát doanh thu" tabIndex={0}>
              <table style={{ width: "100%", textAlign: "left", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ color: "var(--gold)" }}>
                    <th style={{ padding: "8px" }}>Ngày</th>
                    <th style={{ padding: "8px" }}>Số hóa đơn</th>
                    <th style={{ padding: "8px" }}>Doanh thu</th>
                  </tr>
                </thead>
                <tbody>
                  {daily.map((d) => (
                    <tr key={d.date} style={{ borderBottom: "1px solid #332820" }}>
                      <td style={{ padding: "8px" }}>{d.date}</td>
                      <td style={{ padding: "8px" }}>{d.billCount}</td>
                      <td style={{ padding: "8px" }}>{vnd(d.revenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </div>
  );
}
