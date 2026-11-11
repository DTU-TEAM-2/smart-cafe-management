import { useState, useEffect } from "react";
import { fetchRevenueStatistics, vnd } from "./billingApi.js";

const PERIODS = [
  { key: "today", label: "Hôm nay (Today)" },
  { key: "in_week", label: "Tuần này (In Week)" },
  { key: "in_month", label: "Tháng này (In Month)" },
  { key: "in_year", label: "Năm nay (In Year)" },
];

export default function StatisticsView({ authorization }) {
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
        const result = await fetchRevenueStatistics({ ...query, authorization, signal: controller.signal });
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
  }, [query, authorization]);

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
  const maxDayRevenue = daily.reduce((max, day) => {
    const amount = BigInt(day.revenue);
    return amount > max ? amount : max;
  }, 1n);

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

          {/* Biểu đồ hình cột (Column Chart): Doanh thu theo ngày */}
          <section className="billing-chart-panel" aria-labelledby="column-chart-title">
            <h2 id="column-chart-title">Biểu đồ doanh thu</h2>
            <p className="subtitle">
              Doanh thu từng ngày trong kỳ ({summary.from} đến {summary.to}) từ hóa đơn đã thanh toán (PAID).
            </p>

            {daily.length === 0 ? (
              <p style={{ color: "var(--muted)", textAlign: "center", padding: "30px 0" }}>
                Không có dữ liệu trong khoảng thời gian này.
              </p>
            ) : (
              <div className="billing-column-chart" role="img" aria-label="Biểu đồ cột: Doanh thu theo ngày">
                {daily.map((d) => {
                  const amount = BigInt(d.revenue);
                  const height = Number(amount * 10000n / maxDayRevenue) / 100;
                  return (
                    <div key={d.date} className="billing-column-bar-group">
                      {amount > 0n && (
                        <span className="billing-column-value-tip">{vnd(d.revenue)}</span>
                      )}
                      <div
                        className="billing-column-bar"
                        style={{ height: `${height}%` }}
                        title={`${d.date}: ${vnd(d.revenue)} (${d.billCount} hóa đơn)`}
                      />
                      <span className="billing-column-label">{d.date.slice(5)}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {/* Accessible Table for Screen Readers & Verification */}
          <details style={{ marginTop: "16px", color: "var(--muted)", fontSize: "13px" }}>
            <summary style={{ cursor: "pointer", fontWeight: 600 }}>
              Xem bảng đối soát dữ liệu chi tiết
            </summary>
            <div style={{ marginTop: "12px", background: "#1b1612", padding: "16px", borderRadius: "8px" }}>
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
