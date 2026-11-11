import { useState } from "react";
import { useSalesResource, vnd } from "./salesApi.js";

const periods = [["today", "Hôm nay"], ["in_week", "Tuần này"], ["in_month", "Tháng này"], ["in_year", "Năm nay"]];
export function LoadState({ resource }) {
  if (resource.loading) return <p role="status">Đang tải dữ liệu…</p>;
  if (resource.error) return <div role="alert" className="sale-error"><p>{resource.error.message}</p>
    <button type="button" onClick={resource.reload}>Thử lại</button></div>;
  return null;
}

export default function IncomePanel({ authorization, credentials }) {
  const [period, setPeriod] = useState("today");
  const [query, setQuery] = useState("period=today");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [invalid, setInvalid] = useState(false);
  const resource = useSalesResource(`/api/v1/income?${query}`, authorization, credentials);
  const week = useSalesResource(period === "today" ? "/api/v1/income?period=in_week" : null, authorization, credentials);
  const data = resource.data;
  const chart = period === "today" ? week.data : data;
  const days = chart?.dailyRevenue ?? [];
  const maximum = days.reduce((max, day) => BigInt(day.revenue) > max ? BigInt(day.revenue) : max, 1n);
  // Use seven columns for the default week; longer reports keep every date in the data table.
  const bars = days.length <= 31 ? days : [];
  return <>
    <h1>Thu nhập</h1>
    <p className="sale-muted">Doanh thu từ hóa đơn đã xác nhận thanh toán · Giờ Việt Nam</p>
    <div className="sale-panel">
      <div className="sale-actions" aria-label="Kỳ thu nhập">{periods.map(([key, label]) => <button type="button"
        aria-pressed={period === key} key={key} onClick={() => { setPeriod(key); setQuery(`period=${key}`); setInvalid(false); }}>{label}</button>)}</div>
      <form className="sale-actions sale-date-form" onSubmit={event => {
        event.preventDefault();
        if (!from || !to || from > to) { setInvalid(true); return; }
        setInvalid(false); setPeriod("custom"); setQuery(new URLSearchParams({ from, to }).toString());
      }}>
        <label>Từ ngày<input type="date" required value={from} onChange={event => setFrom(event.target.value)} aria-invalid={invalid} aria-describedby={invalid ? "sale-date-error" : undefined} /></label>
        <label>Đến ngày<input type="date" required value={to} onChange={event => setTo(event.target.value)} aria-invalid={invalid} aria-describedby={invalid ? "sale-date-error" : undefined} /></label>
        <button className="sale-primary" type="submit">Tính thu nhập</button>
      </form>
      {invalid && <p id="sale-date-error" role="alert">Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.</p>}
    </div>
    <LoadState resource={resource} />
    {data && <div className="sale-metrics">
      <div className="sale-panel"><h2>{period === "today" ? "Doanh thu hôm nay" : "Doanh thu trong kỳ"}</h2><strong>{vnd(data.totalAmount)}</strong></div>
      <div className="sale-panel"><h2>Hóa đơn</h2><strong>{data.billCount}</strong></div>
      <div className="sale-panel"><h2>Giá trị trung bình</h2><strong>{vnd(data.averageBillAmount)}</strong></div>
    </div>}
    {period === "today" && <LoadState resource={week} />}
    {chart && <section className="sale-panel" aria-labelledby="revenue-chart-title">
      <h2 id="revenue-chart-title">Biểu đồ doanh thu</h2>
      <p className="sale-muted">{chart.from} đến {chart.to}</p>
      {bars.length > 0 && <div className="sale-chart" aria-hidden="true">{bars.map(day => <div key={day.date}>
        <span style={{ height: `${Number(BigInt(day.revenue) * 100n / maximum)}%` }} />
        <small>{day.date.slice(8)}</small>
      </div>)}</div>}
      <details><summary>Số liệu doanh thu theo ngày</summary><div className="sale-table-wrap"><table>
        <thead><tr><th scope="col">Ngày</th><th scope="col">Hóa đơn</th><th scope="col">Doanh thu</th></tr></thead>
        <tbody>{days.map(day => <tr key={day.date}><td>{day.date}</td><td>{day.billCount}</td><td>{vnd(day.revenue)}</td></tr>)}</tbody>
      </table></div></details>
    </section>}
  </>;
}
