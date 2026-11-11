import { useState, useEffect } from "react";
import { fetchRevenueStatistics, vnd } from "./billingApi.js";

const PERIODS = [
  { key: "today", label: "Hôm nay (Today)" },
  { key: "in_week", label: "Tuần này (In Week)" },
  { key: "in_month", label: "Tháng này (In Month)" },
  { key: "in_year", label: "Năm nay (In Year)" },
];

const PIE_COLORS = [
  "#d9b573", // gold
  "#8cb369", // sage green
  "#e07a5f", // terracotta
  "#4ea8de", // ocean blue
  "#f4a261", // amber
  "#9d4edd", // violet
  "#57cc99", // emerald
  "#e63946", // ruby
  "#b5838d", // dusky rose
  "#6d597a", // plum
];

export default function StatisticsView({ authorization }) {
  const [period, setPeriod] = useState("in_month");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [isCustom, setIsCustom] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    async function loadStats() {
      setLoading(true);
      setError("");
      try {
        const res = await fetchRevenueStatistics({
          period: isCustom ? undefined : period,
          from: isCustom ? dateFrom : undefined,
          to: isCustom ? dateTo : undefined,
          authorization,
          signal: controller.signal,
        });
        setData(res);
      } catch (err) {
        if (!controller.signal.aborted) {
          if (err.message === "UNAUTHORIZED") {
            setError("Bạn không có quyền truy cập số liệu thống kê (Yêu cầu quyền ADMIN).");
          } else {
            setError(err.message || "Lỗi tải số liệu thống kê.");
          }
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }
    loadStats();
    return () => controller.abort();
  }, [period, isCustom, dateFrom, dateTo, authorization]);

  function handlePeriodChange(p) {
    setIsCustom(false);
    setDateFrom("");
    setDateTo("");
    setPeriod(p);
  }

  function handleCustomSearch(e) {
    e.preventDefault();
    if (!dateFrom || !dateTo) {
      setError("Vui lòng chọn cả ngày bắt đầu và kết thúc.");
      return;
    }
    if (dateFrom > dateTo) {
      setError("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
      return;
    }
    setError("");
    setIsCustom(true);
  }

  const summary = data?.summary;
  const daily = data?.dailyRevenue || [];
  const itemSales = data?.itemSales || [];

  // Calculate maximum for column chart bar heights
  const maxDayRevenue = daily.reduce((max, d) => {
    try {
      const val = BigInt(d.revenue);
      return val > max ? val : max;
    } catch {
      return max;
    }
  }, 1n);

  // SVG Pie chart calculation
  let cumulativeAngle = 0;
  const pieSlices = itemSales.map((item, idx) => {
    const pct = item.percentage / 100;
    const startAngle = cumulativeAngle;
    const sliceAngle = pct * 2 * Math.PI;
    cumulativeAngle += sliceAngle;
    const endAngle = cumulativeAngle;

    // SVG coordinates (radius = 90, center = 100, 100)
    const x1 = 100 + 85 * Math.cos(startAngle - Math.PI / 2);
    const y1 = 100 + 85 * Math.sin(startAngle - Math.PI / 2);
    const x2 = 100 + 85 * Math.cos(endAngle - Math.PI / 2);
    const y2 = 100 + 85 * Math.sin(endAngle - Math.PI / 2);
    const largeArc = sliceAngle > Math.PI ? 1 : 0;

    const pathData = itemSales.length === 1 || pct >= 0.999
      ? `M 100,15 A 85,85 0 1,1 99.99,15 Z`
      : `M 100,100 L ${x1},${y1} A 85,85 0 ${largeArc},1 ${x2},${y2} Z`;

    return {
      pathData,
      color: PIE_COLORS[idx % PIE_COLORS.length],
      item,
    };
  });

  return (
    <div className="billing-statistics-section">
      <div className="billing-control-panel">
        <div className="billing-search-types" role="radiogroup" aria-label="Kỳ thống kê">
          {PERIODS.map((p) => (
            <label key={p.key}>
              <input
                type="radio"
                name="stat_period"
                checked={!isCustom && period === p.key}
                onChange={() => handlePeriodChange(p.key)}
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

      {error && (
        <div className="billing-message-banner error" role="alert" style={{ marginBottom: "20px" }}>
          {error}
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
            <h2 id="column-chart-title">Biểu đồ hình cột — Doanh thu theo thời gian</h2>
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
                  let pct = 0;
                  try {
                    pct = Number(BigInt(d.revenue) * 100n / maxDayRevenue);
                  } catch {
                    pct = 0;
                  }
                  return (
                    <div key={d.date} className="billing-column-bar-group">
                      {BigInt(d.revenue || "0") > 0n && (
                        <span className="billing-column-value-tip">{vnd(d.revenue)}</span>
                      )}
                      <div
                        className="billing-column-bar"
                        style={{ height: `${Math.max(pct, 3)}%` }}
                        title={`${d.date}: ${vnd(d.revenue)} (${d.billCount} hóa đơn)`}
                      />
                      <span className="billing-column-label">{d.date.slice(5)}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {/* Biểu đồ hình tròn (Pie Chart): Tỷ trọng doanh thu theo món */}
          <section className="billing-chart-panel" aria-labelledby="pie-chart-title">
            <h2 id="pie-chart-title">Biểu đồ hình tròn — Tỷ trọng doanh thu theo món</h2>
            <p className="subtitle">
              Phân bố cơ cấu doanh thu theo từng món gọi trong cùng tập hóa đơn thanh toán của kỳ.
            </p>

            {itemSales.length === 0 ? (
              <p style={{ color: "var(--muted)", textAlign: "center", padding: "30px 0" }}>
                Chưa có dữ liệu bán món trong kỳ thống kê này.
              </p>
            ) : (
              <div className="billing-pie-layout">
                <svg
                  viewBox="0 0 200 200"
                  className="billing-pie-svg"
                  role="img"
                  aria-label="Biểu đồ tròn: Cơ cấu doanh thu theo món ăn"
                >
                  {pieSlices.map((slice, i) => (
                    <path
                      key={slice.item.itemCode || i}
                      d={slice.pathData}
                      fill={slice.color}
                      stroke="#211b17"
                      strokeWidth="2"
                    >
                      <title>{`${slice.item.itemName}: ${vnd(slice.item.totalRevenue || slice.item.revenue)} (${slice.item.percentage}%)`}</title>
                    </path>
                  ))}
                  {/* Inner donut circle for modern aesthetic */}
                  <circle cx="100" cy="100" r="42" fill="#211b17" />
                  <text
                    x="100"
                    y="98"
                    textAnchor="middle"
                    fill="var(--gold)"
                    fontSize="11"
                    fontWeight="bold"
                  >
                    LECKER
                  </text>
                  <text
                    x="100"
                    y="112"
                    textAnchor="middle"
                    fill="var(--muted)"
                    fontSize="9"
                  >
                    Món bán
                  </text>
                </svg>

                <ul className="billing-pie-legend">
                  {itemSales.map((item, idx) => (
                    <li key={item.itemCode}>
                      <span
                        className="billing-pie-legend-color"
                        style={{ background: PIE_COLORS[idx % PIE_COLORS.length] }}
                      />
                      <span className="billing-pie-legend-name">{item.itemName}</span>
                      <span style={{ color: "var(--muted)", fontSize: "12px" }}>
                        {item.totalQuantity ?? item.quantity} phần
                      </span>
                      <strong className="billing-pie-legend-stat">
                        {vnd(item.totalRevenue || item.revenue)} ({item.percentage}%)
                      </strong>
                    </li>
                  ))}
                </ul>
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
