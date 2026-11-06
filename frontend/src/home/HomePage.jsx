import { useEffect, useState } from "react";

const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" });
const specials = [
  { title: "Đồ uống nổi bật", description: "Sáng tạo hương vị" },
  { title: "Không gian quán", description: "Ấm áp và thư giãn" },
  { title: "Đội ngũ Barista", description: "Chuyên tâm từng tách" },
];

function validList(items, amount) {
  return Array.isArray(items) && items.length <= 5 && items.every((item) =>
    typeof item?.id === "string" && /^[1-9]\d{0,18}$/.test(item.id) && typeof item.name === "string" && item.name.trim()
    && typeof item[amount] === "string" && /^[1-9]\d{0,29}$/.test(item[amount]));
}

function Rankings({ status, data, retry }) {
  const max = Number(data?.mostOrdered[0]?.orderedQuantity ?? 1);
  return <>
    {status === "error" && <div className="error-banner" role="alert">
      <span>Chưa tải được danh sách món.</span><button type="button" onClick={retry}>Thử lại</button>
    </div>}
    <div className="rankings" aria-busy={status === "loading"}>
      <section id="newest" aria-labelledby="newest-title">
        <h2 id="newest-title">5 món mới nhất</h2>
        <RankingList status={status} items={data?.newest} empty="Chưa có món mới."
          value={(item) => <strong>{currency.format(BigInt(item.price))}</strong>} />
      </section>
      <section id="most-ordered" aria-labelledby="most-ordered-title">
        <h2 id="most-ordered-title">5 món được gọi nhiều</h2>
        <RankingList status={status} items={data?.mostOrdered} empty="Chưa có lượt gọi món."
          value={(item) => <meter className="rank-meter" min="0" max={max} value={Number(item.orderedQuantity)}
            aria-label={`${item.orderedQuantity} món đã gọi`} />} />
      </section>
    </div>
  </>;
}

function RankingList({ status, items = [], empty, value }) {
  if (status === "loading") return <p className="ranking-panel ranking-message" role="status">Đang tải danh sách món…</p>;
  if (status === "error") return <p className="ranking-panel ranking-message">Dữ liệu hiện chưa khả dụng.</p>;
  if (!items.length) return <p className="ranking-panel ranking-message">{empty}</p>;
  return <ol className="ranking-panel ranking-list">
    {items.map((item, index) => <li key={item.id}>
      <span className="rank-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>
      <span className="rank-name">{item.name}</span>
      {value(item)}
    </li>)}
  </ol>;
}

export default function HomePage() {
  const [result, setResult] = useState({ status: "loading", data: null });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const timeout = AbortSignal.timeout(10000);
    const signal = AbortSignal.any([controller.signal, timeout]);
    async function load() {
      try {
        const response = await fetch("/api/v1/home/highlights", { signal });
        if (!response.ok) throw new Error("Highlights request failed");
        const data = await response.json();
        if (!validList(data?.newest, "price") || !validList(data?.mostOrdered, "orderedQuantity")) {
          throw new Error("Invalid highlights");
        }
        if (!controller.signal.aborted) setResult({ status: "ready", data });
      } catch {
        if (!controller.signal.aborted) setResult({ status: "error", data: null });
      }
    }
    void load();
    return () => controller.abort();
  }, [attempt]);

  return <div className="home">
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-art" aria-hidden="true">
        <div className="coffee-marks">{[0, 1, 2, 3].map((key) => <span key={key} />)}</div>
      </div>
      <div className="hero-copy">
        <p className="eyebrow">Bản sắc cà phê</p>
        <h1 id="hero-title"><span>High Quality</span> COFFEE</h1>
        <p className="hero-subtitle">Mỗi tách cà phê là một câu chuyện</p>
        <a className="hero-cta" href="#newest">Khám phá menu <span aria-hidden="true">→</span></a>
      </div>
    </section>

    <section id="about" className="specials" aria-labelledby="specials-title">
      <h2 id="specials-title">Our Specials — Câu chuyện Lecker</h2>
      <div className="special-grid">
        {specials.map(({ title, description }) => <article className="special" key={title}>
          <div className="special-tile" aria-hidden="true" />
          <div><h3>{title}</h3><p>{description}</p></div>
        </article>)}
      </div>
    </section>

    <Rankings status={result.status} data={result.data} retry={() => {
      setResult({ status: "loading", data: null });
      setAttempt((value) => value + 1);
    }} />
  </div>;
}
